-- The referential of territories (D-090, docs/integration/territoires.md). Provisional number,
-- renumbered by the main session when entering main if needed.
--
-- Public statistics of the Insee (populations, registered voters) on the communes,
-- intercommunalities, departments, collectivities and regions, with their official codes: nothing
-- about a person. Nothing about the readers either: no client address, no request, no package
-- downloaded by whom.
--
-- 1. territoires_import: one row per import attempt. The last successful one (`ready`) is served; a
--    new import replaces it only once it has succeeded (older rows are deleted by the application).
-- 2. territoires_package: the documents of an import (department and overseas packages, index of
--    the names of the communes), compressed with gzip; `sha256` and `bytes` describe the exact
--    uncompressed JSON body, as the national level announces them.

CREATE TABLE territoires_import (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- `example` (real public figures for a few departments, embedded in the server) or `official`.
    source          TEXT        NOT NULL CHECK (source IN ('example', 'official')),
    status          TEXT        NOT NULL CHECK (status IN ('building', 'ready', 'failed')),
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    -- Numbers compared by the guard against a truncated source: {"communes": 34875, "population": 68094280}.
    counts          JSONB,
    -- The national level (GET /api/v1/territoires), exact UTF-8 bytes, and its SHA-256.
    national_body   BYTEA,
    national_sha256 TEXT        CHECK (national_sha256 IS NULL OR national_sha256 ~ '^[0-9a-f]{64}$'),
    -- Why an attempt failed: a short code written by the server (never a value read from a file).
    failure         TEXT        CHECK (failure IS NULL OR length(failure) <= 200),
    -- A served import always has its national level; a failed attempt never has one.
    CHECK (status <> 'ready' OR (national_body IS NOT NULL AND national_sha256 IS NOT NULL)),
    CHECK (status <> 'failed' OR national_body IS NULL)
);

-- At most one import is served, and at most one is being built.
CREATE UNIQUE INDEX territoires_import_ready_idx ON territoires_import (status) WHERE status IN ('ready', 'building');

CREATE TABLE territoires_package (
    import_id BIGINT  NOT NULL REFERENCES territoires_import (id) ON DELETE CASCADE,
    -- A department, an overseas collectivity, or `communes` for the index of the names.
    code      TEXT    NOT NULL CHECK (code ~ '^([0-9]{2,3}|2A|2B|communes)$'),
    name      TEXT    NOT NULL,
    sha256    TEXT    NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    bytes     INTEGER NOT NULL CHECK (bytes > 0),
    body_gzip BYTEA   NOT NULL,
    PRIMARY KEY (import_id, code)
);
