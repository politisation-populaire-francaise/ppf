-- The legal register of level 1 (decision D-077; legal note docs/integration/lcen-conservation-identification.md,
-- sections 4, 6.8 and 7.1): what the law requires a host to keep about the contributions to PUBLIC contents
-- (LCEN, article 6, V, A; décret n° 2021-1362, articles 3, 5, 6 and 7; injunction décret n° 2026-920), and
-- nothing else. Provisional number: renumbered to the next free one when it enters main.
--
-- Only pseudonymous data the server already holds in the signed events: event id, nature of the operation,
-- reception date, the event's own date, kind, author key, protocol type; for a replacement or a deletion, the
-- id, date and key of the removed version (never its content); per account, the key and the successive
-- pseudonyms (name and display_name of the profile, kind 0). NEVER an IP address or a port, a browser, an
-- e-mail address or a phone number, a content text, a reading, a vote, a support, a private message, a report.
--
-- Separate schema, never read by any feature of the platform. The restricted role of the server may only
-- INSERT the columns it fills (never the reception date, set by the database, so that no row can be
-- backdated), never SELECT, UPDATE, DELETE or TRUNCATE (afterMigrate.sql). Reading happens only through
-- answer_requisition (a judicial requisition approved by two people in the back-office), deleting only
-- through purge, which cannot delete anything younger than one year.
--
-- Retention (docs/REGISTRE_DONNEES.md): one year from the operation for content_operation; one year after
-- the last recorded operation of the account for account_pseudonym (a Nostr account has no closure date:
-- approximation until the terms of use define the closure of an account); requisition_answer keeps only
-- counts and is kept without limit (it feeds the public counter of answered requisitions).

CREATE SCHEMA legal_register;

CREATE TABLE legal_register.content_operation (
    id                 BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The event that made the operation: the new content, its new version, or the deletion request (kind 5).
    event_id           TEXT        NOT NULL CHECK (event_id ~ '^[0-9a-f]{64}$'),
    operation          TEXT        NOT NULL CHECK (operation IN ('create', 'replace', 'delete')),
    -- Reception by the server, UTC, to the second; set by the database only.
    received_at        TIMESTAMPTZ NOT NULL DEFAULT date_trunc('second', now()),
    -- The date written in the event by its author (NIP-01 created_at, seconds).
    created_at         BIGINT      NOT NULL CHECK (created_at >= 0),
    kind               INTEGER     NOT NULL CHECK (kind BETWEEN 0 AND 65535),
    -- The author's public key (pseudonymous), the only identifier the platform knows.
    pubkey             TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- Type of protocol of the connection (décret 2021-1362, article 5, 2°), one byte: 1 = HTTPS API.
    protocol           SMALLINT    NOT NULL CHECK (protocol BETWEEN 1 AND 255),
    -- For a replacement or a deletion: the removed version, never its content.
    removed_event_id   TEXT        CHECK (removed_event_id ~ '^[0-9a-f]{64}$'),
    removed_created_at BIGINT      CHECK (removed_created_at >= 0),
    removed_pubkey     TEXT        CHECK (removed_pubkey ~ '^[0-9a-f]{64}$'),
    CHECK ((operation = 'create') = (removed_event_id IS NULL)),
    CHECK ((removed_event_id IS NULL) = (removed_created_at IS NULL)),
    CHECK ((removed_event_id IS NULL) = (removed_pubkey IS NULL))
);

CREATE INDEX content_operation_pubkey_idx ON legal_register.content_operation (pubkey, received_at);
CREATE INDEX content_operation_event_idx ON legal_register.content_operation (event_id);
CREATE INDEX content_operation_removed_idx ON legal_register.content_operation (removed_event_id) WHERE removed_event_id IS NOT NULL;
CREATE INDEX content_operation_received_idx ON legal_register.content_operation (received_at);

CREATE TABLE legal_register.account_pseudonym (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pubkey           TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- The two pseudonym fields of the profile (NIP-01 and NIP-24), NULL when absent; nothing else of the profile.
    name             TEXT        CHECK (length(name) <= 1000),
    display_name     TEXT        CHECK (length(display_name) <= 1000),
    -- When the server received the profile that made the change, UTC, to the second; set by the database only.
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT date_trunc('second', now()),
    -- The profile event (kind 0) that made the change, and its own date.
    event_id         TEXT        NOT NULL CHECK (event_id ~ '^[0-9a-f]{64}$'),
    event_created_at BIGINT      NOT NULL CHECK (event_created_at >= 0)
);

CREATE INDEX account_pseudonym_pubkey_idx ON legal_register.account_pseudonym (pubkey, changed_at);
CREATE INDEX account_pseudonym_changed_idx ON legal_register.account_pseudonym (changed_at);

-- One row per requisition answered: the approval of the back-office it answers (at most once) and counts.
-- Never the reference, the search nor the rows (the reference stays in the team journal).
CREATE TABLE legal_register.requisition_answer (
    approval_id    UUID        PRIMARY KEY,
    answered_at    TIMESTAMPTZ NOT NULL DEFAULT date_trunc('second', now()),
    operation_rows   INTEGER     NOT NULL CHECK (operation_rows >= 0),
    pseudonym_rows INTEGER     NOT NULL CHECK (pseudonym_rows >= 0)
);

-- Deletes what is older than the retention, and nothing younger: one year from the operation for the
-- content operations; for the pseudonyms, only those of an account whose every recorded operation and
-- pseudonym change is older than one year. The date is the database's own: no caller can move it.
CREATE FUNCTION legal_register.purge() RETURNS TABLE (deleted_operations BIGINT, deleted_pseudonyms BIGINT)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    cutoff TIMESTAMPTZ := now() - interval '1 year';
    operations BIGINT;
    pseudonyms BIGINT;
BEGIN
    DELETE FROM legal_register.account_pseudonym p
    WHERE p.changed_at < cutoff
      AND NOT EXISTS (SELECT 1 FROM legal_register.content_operation c WHERE c.pubkey = p.pubkey AND c.received_at >= cutoff)
      AND NOT EXISTS (SELECT 1 FROM legal_register.account_pseudonym q WHERE q.pubkey = p.pubkey AND q.changed_at >= cutoff);
    GET DIAGNOSTICS pseudonyms = ROW_COUNT;
    DELETE FROM legal_register.content_operation WHERE received_at < cutoff;
    GET DIAGNOSTICS operations = ROW_COUNT;
    RETURN QUERY SELECT operations, pseudonyms;
END;
$$;

-- The only reading of the register: the answer to a judicial requisition approved by two people in the
-- back-office (action legal.requisition, approved by somebody else than the requester), at most once. The
-- search is read from the approval itself (a public key and/or an event id, and a period), never given by
-- the caller; afterwards it is erased from the approval, which keeps only the counts. Returns the rows as
-- JSON: the content operations of the period, and the pseudonyms of the keys concerned (the changes in the
-- period and the pseudonym in force at its start). Refusals: LR001 not approved, LR002 already answered,
-- LR003 incomplete search.
CREATE FUNCTION legal_register.answer_requisition(p_approval UUID) RETURNS JSONB
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    request RECORD;
    search JSONB;
    v_pubkey TEXT;
    v_event TEXT;
    v_from TIMESTAMPTZ;
    v_until TIMESTAMPTZ;
    v_keys TEXT[];
    operations JSONB;
    pseudonyms JSONB;
    n_operations INTEGER;
    n_pseudonyms INTEGER;
BEGIN
    SELECT a.action, a.permission, a.status, a.payload, a.requested_by, a.decided_by INTO request
    FROM "${flyway:defaultSchema}".team_pending_approval a WHERE a.id = p_approval FOR UPDATE;
    IF NOT FOUND OR request.action <> 'legal.requisition' OR request.permission <> 'legal.requisition'
       OR request.status <> 'approved' OR request.decided_by IS NULL OR request.decided_by = request.requested_by THEN
        RAISE EXCEPTION 'requisition not approved by a second person' USING ERRCODE = 'LR001';
    END IF;
    IF EXISTS (SELECT 1 FROM legal_register.requisition_answer r WHERE r.approval_id = p_approval) THEN
        RAISE EXCEPTION 'requisition already answered' USING ERRCODE = 'LR002';
    END IF;
    search := request.payload::jsonb;
    v_pubkey := NULLIF(search ->> 'pubkey', '');
    v_event := NULLIF(search ->> 'event', '');
    IF (v_pubkey IS NULL AND v_event IS NULL)
       OR COALESCE(search ->> 'from', '') !~ '^[0-9]{1,12}$' OR COALESCE(search ->> 'until', '') !~ '^[0-9]{1,12}$' THEN
        RAISE EXCEPTION 'requisition search incomplete' USING ERRCODE = 'LR003';
    END IF;
    v_from := to_timestamp((search ->> 'from')::BIGINT);
    v_until := to_timestamp((search ->> 'until')::BIGINT);
    IF v_until <= v_from THEN
        RAISE EXCEPTION 'requisition search incomplete' USING ERRCODE = 'LR003';
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
               'event_id', c.event_id,
               'operation', c.operation,
               'received_at', to_char(c.received_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
               'created_at', c.created_at,
               'kind', c.kind,
               'pubkey', c.pubkey,
               'protocol', CASE c.protocol WHEN 1 THEN 'https-api' ELSE c.protocol::TEXT END,
               'removed', CASE WHEN c.removed_event_id IS NULL THEN NULL ELSE jsonb_build_object(
                   'event_id', c.removed_event_id, 'created_at', c.removed_created_at, 'pubkey', c.removed_pubkey) END
           )) ORDER BY c.received_at, c.id), '[]'::JSONB),
           count(*),
           array_agg(DISTINCT c.pubkey)
    INTO operations, n_operations, v_keys
    FROM legal_register.content_operation c
    WHERE c.received_at >= v_from AND c.received_at < v_until
      AND (v_pubkey IS NULL OR c.pubkey = v_pubkey OR c.removed_pubkey = v_pubkey)
      AND (v_event IS NULL OR c.event_id = v_event OR c.removed_event_id = v_event);
    IF v_pubkey IS NOT NULL THEN
        v_keys := ARRAY[v_pubkey];
    END IF;

    SELECT COALESCE(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
               'pubkey', p.pubkey,
               'name', p.name,
               'display_name', p.display_name,
               'changed_at', to_char(p.changed_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'),
               'event_id', p.event_id,
               'event_created_at', p.event_created_at
           )) ORDER BY p.pubkey, p.changed_at, p.id), '[]'::JSONB),
           count(*)
    INTO pseudonyms, n_pseudonyms
    FROM legal_register.account_pseudonym p
    WHERE p.pubkey = ANY(COALESCE(v_keys, ARRAY[]::TEXT[]))
      AND ((p.changed_at >= v_from AND p.changed_at < v_until)
           OR p.id = (SELECT q.id FROM legal_register.account_pseudonym q
                      WHERE q.pubkey = p.pubkey AND q.changed_at < v_from
                      ORDER BY q.changed_at DESC, q.id DESC LIMIT 1));

    INSERT INTO legal_register.requisition_answer (approval_id, operation_rows, pseudonym_rows)
    VALUES (p_approval, n_operations, n_pseudonyms);
    -- The search leaves the approval: only the counts and the date of the answer stay there.
    UPDATE "${flyway:defaultSchema}".team_pending_approval
    SET payload = ((payload::jsonb - 'pubkey' - 'event' - 'from' - 'until') || jsonb_build_object(
            'answered_at', extract(epoch FROM date_trunc('second', now()))::BIGINT::TEXT,
            'operations', n_operations::TEXT,
            'pseudonyms', n_pseudonyms::TEXT))::TEXT
    WHERE id = p_approval;
    RETURN jsonb_build_object('content_operations', operations, 'pseudonyms', pseudonyms);
END;
$$;

-- The public counter: requisitions answered per month (UTC), never a reference, a key nor a date finer.
CREATE FUNCTION legal_register.requisitions_by_month() RETURNS TABLE (month TEXT, answered BIGINT)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
    SELECT to_char(r.answered_at AT TIME ZONE 'UTC', 'YYYY-MM'), count(*)
    FROM legal_register.requisition_answer r
    GROUP BY 1
    ORDER BY 1
$$;

-- Functions are executable by everyone by default: only the restricted role of the server gets them
-- (afterMigrate.sql), the owner keeps them.
REVOKE ALL ON FUNCTION legal_register.purge() FROM PUBLIC;
REVOKE ALL ON FUNCTION legal_register.answer_requisition(UUID) FROM PUBLIC;
REVOKE ALL ON FUNCTION legal_register.requisitions_by_month() FROM PUBLIC;
