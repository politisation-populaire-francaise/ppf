-- Demands proposed by citizens (kind 30023, ppf-type = revendication, signed by a key outside the
-- editorial team), held for review by a person before publication (decision of the owner,
-- 2026-11-05). An approved demand is copied into events; this row is only the review file.
--
-- Nothing about the request is stored: no client address, no header, no device information.
-- Retention: a decided proposal (approved or rejected) is deleted 30 days after the decision by the
-- application (PPF_PROPOSALS_RETENTION_DAYS); a pending one stays until the team decides.

CREATE TABLE revendication_proposal (
    -- Id of the signed event (NIP-01).
    id              TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    -- Pseudonymous public key of the author, as in events.pubkey.
    pubkey          TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    -- The `d` tag: with the pubkey, the stable address of the demand (30023:pubkey:d).
    identifier      TEXT        NOT NULL CHECK (length(identifier) BETWEEN 1 AND 128),
    -- created_at of the event, to know which version of an address is the newest.
    created_at      BIGINT      NOT NULL CHECK (created_at >= 0),
    -- The signed event, as received (JSON).
    event           TEXT        NOT NULL,
    -- Its tags (title, summary, territory, themes...), also in the event: the duplicate check of
    -- the other proposals reads them without parsing every event.
    tags            JSONB       NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    -- The report of the automatic checks (ProposalReport, JSON); NULL until the checks ran.
    report          JSONB,
    received_at     TIMESTAMPTZ NOT NULL,
    decided_at      TIMESTAMPTZ,
    -- Reason given by the team when it rejects a proposal, shown to the author.
    decision_reason TEXT        CHECK (decision_reason IS NULL OR length(decision_reason) <= 1000),
    CHECK ((status = 'pending') = (decided_at IS NULL))
);

-- One pending version per address: a new version replaces the pending one.
-- Also serves the count of pending proposals per key.
CREATE UNIQUE INDEX revendication_proposal_pending_address_idx
    ON revendication_proposal (pubkey, identifier) WHERE status = 'pending';

-- The oldest pending proposals first (review order, checks to catch up).
CREATE INDEX revendication_proposal_pending_received_idx
    ON revendication_proposal (received_at, id) WHERE status = 'pending';

-- Deletion of the decided proposals by decision date.
CREATE INDEX revendication_proposal_decided_at_idx
    ON revendication_proposal (decided_at) WHERE status <> 'pending';
