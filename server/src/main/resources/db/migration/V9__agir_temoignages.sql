-- Testimonies of the Agir space (M5: AG-07, AG-08, AG-54; docs/integration/agir-temoignages.md).
-- Provisional number: renumbered to the next free version when the branch enters main.
--
-- The server never reads a testimony: it keeps two kinds of encrypted envelopes (NIP-59 gift
-- wraps signed by one-time keys) and encrypted files named by their SHA-256. Nothing about the
-- request is stored (no client address, no header, no device information); dates are rounded down
-- to the hour.
--
-- 1. testimony_submission: one row per testimony, the envelope for the moderation team (`p` = the
--    moderation key), its review status, the team's reason for a refusal, and the id of the public
--    testimony the team published (kind 30023, signed by an editorial key). Deleted by the
--    application PPF_TESTIMONY_RETENTION_DAYS (30) after the decision; pending ones stay until the
--    team decides.
-- 2. testimony_evidence: the envelope for the evidence key (`p` = the key of the conservation of
--    evidence, which the moderators do not hold), only when the witness chose to give the untouched
--    original for the partner lawyers (AG-08). Kept PPF_EVIDENCE_RETENTION_DAYS (2190, six years:
--    the limitation period of most offences, article 8 of the code de procédure pénale), whatever
--    the decision on the testimony.
-- 3. testimony_file: the encrypted files on disk (PPF_TESTIMONY_DIR), by SHA-256 of the ciphertext,
--    with the envelope that names them; a file that no envelope claims within 24 hours is deleted.
-- 4. published_media: the photos of published testimonies, cleaned on the device and checked again
--    by the server, served at /api/v1/media/<sha256>; they live as long as the public testimony.

CREATE TABLE testimony_submission (
    id              TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    envelope        TEXT        NOT NULL,
    status          TEXT        NOT NULL CHECK (status IN ('pending', 'approved', 'rejected')),
    received_at     BIGINT      NOT NULL,
    decided_at      BIGINT,
    reason          TEXT        CHECK (reason IS NULL OR length(reason) BETWEEN 1 AND 1000),
    published_id    TEXT        CHECK (published_id IS NULL OR published_id ~ '^[0-9a-f]{64}$'),
    -- The envelope for the evidence key sent with this testimony, if any (testimony_evidence.id).
    evidence_id     TEXT        CHECK (evidence_id IS NULL OR evidence_id ~ '^[0-9a-f]{64}$')
);

CREATE INDEX testimony_submission_pending ON testimony_submission (received_at) WHERE status = 'pending';

CREATE TABLE testimony_evidence (
    id              TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    envelope        TEXT        NOT NULL,
    received_at     BIGINT      NOT NULL,
    expires_at      BIGINT      NOT NULL
);

CREATE TABLE testimony_file (
    id              TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    size            BIGINT      NOT NULL CHECK (size > 0),
    received_at     BIGINT      NOT NULL,
    -- The envelope that names the file: a testimony_submission id or a testimony_evidence id; null until claimed.
    owner           TEXT        CHECK (owner IS NULL OR owner ~ '^[0-9a-f]{64}$'),
    evidence        BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX testimony_file_owner ON testimony_file (owner);

CREATE TABLE published_media (
    sha256          TEXT        PRIMARY KEY CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    mime            TEXT        NOT NULL CHECK (mime IN ('image/jpeg', 'image/png', 'image/webp')),
    size            BIGINT      NOT NULL CHECK (size > 0),
    event_id        TEXT        NOT NULL CHECK (event_id ~ '^[0-9a-f]{64}$')
);
