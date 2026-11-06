-- Signed events (NIP-01), the envelope of every published content. See docs/FORMATS.md.
-- No personal data beyond the author's pseudonymous public key; no client address is ever stored.

CREATE TABLE events (
    id          TEXT        PRIMARY KEY CHECK (id ~ '^[0-9a-f]{64}$'),
    pubkey      TEXT        NOT NULL CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    created_at  BIGINT      NOT NULL CHECK (created_at >= 0),
    kind        INTEGER     NOT NULL CHECK (kind BETWEEN 0 AND 65535),
    tags        JSONB       NOT NULL,
    content     TEXT        NOT NULL,
    sig         TEXT        NOT NULL CHECK (sig ~ '^[0-9a-f]{128}$'),
    -- Replacement key (kind:pubkey or kind:pubkey:d) for replaceable and addressable kinds; NULL otherwise.
    address     TEXT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX events_kind_created_at_idx ON events (kind, created_at DESC, id);
CREATE INDEX events_pubkey_created_at_idx ON events (pubkey, created_at DESC, id);
CREATE UNIQUE INDEX events_address_idx ON events (address) WHERE address IS NOT NULL;
