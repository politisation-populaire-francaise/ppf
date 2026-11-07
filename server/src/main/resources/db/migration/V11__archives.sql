-- The timestamped archive (D-111, docs/integration/archivage-horodate.md).
--
-- archive_proof: one row per SHA-256 digest that the server timestamps: the id of a published event
-- (itself a SHA-256 of the signed content), the digest of the text of the Pacte, of a document
-- received... Only digests of public contents: no personal data, no account, no client address, no
-- request. The day of the request is kept as a date, never as an instant.
--
-- Life of a row:
--   queued   -> waiting for the next batch (no proof yet);
--   pending  -> the batch was stamped: the `.ots` proof holds the answers of at least two
--               OpenTimestamps calendars, which still wait for a Bitcoin block;
--   anchored -> a calendar gave a Bitcoin attestation (the proof is complete), not yet checked;
--   verified -> the block header, fetched from two independent explorers, matched the proof.
-- Rows are never deleted by the application: an archive is kept for good.
--
-- What left the server for a row: only the root of its batch (calendars, timestamping authority),
-- intermediate values of the proof (calendars) and block heights and hashes (explorers).

CREATE TABLE archive_proof (
    digest          BYTEA       PRIMARY KEY CHECK (octet_length(digest) = 32),
    state           TEXT        NOT NULL DEFAULT 'queued' CHECK (state IN ('queued', 'pending', 'anchored', 'verified')),
    -- Day (UTC) the digest was queued: a date, not an instant.
    requested_on    DATE        NOT NULL,
    -- The batch: root of its Merkle tree, the only value sent to the calendars and to the TSA.
    batch_root      BYTEA       CHECK (batch_root IS NULL OR octet_length(batch_root) = 32),
    -- The proof in the standard `.ots` format, readable by the official client (`ots verify`).
    ots             BYTEA       CHECK (ots IS NULL OR octet_length(ots) BETWEEN 1 AND 262144),
    -- The Bitcoin block, once verified: height, hash (hexadecimal, as explorers write it) and time
    -- (seconds since 1970, read from the header).
    bitcoin_height  BIGINT      CHECK (bitcoin_height IS NULL OR bitcoin_height BETWEEN 0 AND 4294967295),
    block_hash      TEXT        CHECK (block_hash IS NULL OR block_hash ~ '^[0-9a-f]{64}$'),
    block_time      BIGINT      CHECK (block_time IS NULL OR block_time BETWEEN 0 AND 4294967295),
    -- RFC 3161 token of the batch root (DER TimeStampToken), the time it attests and the SHA-256 of
    -- the certificate that signed it. The same for every row of a batch.
    rfc3161_token   BYTEA       CHECK (rfc3161_token IS NULL OR octet_length(rfc3161_token) BETWEEN 1 AND 65536),
    tsa_time        TIMESTAMPTZ,
    tsa_cert_sha256 BYTEA       CHECK (tsa_cert_sha256 IS NULL OR octet_length(tsa_cert_sha256) = 32),
    CHECK ((state = 'queued') = (batch_root IS NULL)),
    CHECK ((batch_root IS NULL) = (ots IS NULL)),
    CHECK ((state = 'verified') = (bitcoin_height IS NOT NULL)),
    CHECK ((bitcoin_height IS NULL) = (block_hash IS NULL) AND (block_hash IS NULL) = (block_time IS NULL)),
    CHECK ((rfc3161_token IS NULL) = (tsa_time IS NULL) AND (tsa_time IS NULL) = (tsa_cert_sha256 IS NULL)),
    CHECK (rfc3161_token IS NULL OR batch_root IS NOT NULL)
);

-- The periodic tasks read the rows still waiting for something, oldest first.
CREATE INDEX archive_proof_waiting_idx ON archive_proof (state, requested_on) WHERE state <> 'verified';
-- The RFC 3161 token is written for every row of a batch at once.
CREATE INDEX archive_proof_batch_idx ON archive_proof (batch_root) WHERE batch_root IS NOT NULL;
