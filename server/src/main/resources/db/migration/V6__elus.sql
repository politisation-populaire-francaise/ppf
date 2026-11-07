-- The directory of elected officials (contract docs/integration/comprendre-vague-3.md, section 1; D-047).
--
-- The server imports the national directory of elected officials (RNE, ministère de l'Intérieur) or,
-- by default, a fictitious example set, and serves it as one JSON package per department
-- (GET /api/v1/elus/{code}). Only public information about elected officials in their mandate is
-- kept: name, mandate, function, territory, start dates. Never a date of birth, sex, occupation or
-- nationality: those columns of the RNE are never read.
--
-- Nothing about the readers is stored: no client address, no request, no package downloaded by whom.
--
-- 1. elus_import: one row per import attempt. The last successful one (`ready`) is served; a new
--    import replaces it only once it has succeeded; older rows are deleted by the application
--    (only the served import and the last failed attempt remain).
-- 2. elus_package: the packages of an import, compressed with gzip; `sha256` and `bytes` describe the
--    exact uncompressed JSON body (UTF-8), as the index announces them.
-- 3. elus_staging: the officials read from the files of the import being built, emptied as soon as
--    the packages are written. UNLOGGED: rebuilt from the source files if the database restarts.

CREATE TABLE elus_import (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- `example` (fictitious set embedded in the server) or `official` (the RNE, production only).
    source            TEXT        NOT NULL CHECK (source IN ('example', 'official')),
    status            TEXT        NOT NULL CHECK (status IN ('building', 'ready', 'failed')),
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ,
    -- Date of the last update of the RNE, as data.gouv.fr gives it.
    source_updated_on DATE,
    -- Number of officials read per mandate, e.g. {"municipal": 511225, ...}: the guard against a
    -- truncated file compares a new import with the previous one of the same source.
    counts            JSONB,
    -- The index (GET /api/v1/elus), exact UTF-8 bytes, and its SHA-256 in lowercase hexadecimal.
    index_body        BYTEA,
    index_sha256      TEXT        CHECK (index_sha256 IS NULL OR index_sha256 ~ '^[0-9a-f]{64}$'),
    -- Why an attempt failed: a short code written by the server (never a value read from a file).
    failure           TEXT        CHECK (failure IS NULL OR length(failure) <= 200),
    CHECK ((status = 'ready') = (index_body IS NOT NULL AND index_sha256 IS NOT NULL))
);

-- At most one import is served, and at most one is being built.
CREATE UNIQUE INDEX elus_import_ready_idx ON elus_import (status) WHERE status IN ('ready', 'building');

CREATE TABLE elus_package (
    import_id   BIGINT  NOT NULL REFERENCES elus_import (id) ON DELETE CASCADE,
    -- `01` to `95`, `2A`, `2B`, `971` to `976`, the overseas collectivities present in the RNE, or `FR`.
    code        TEXT    NOT NULL CHECK (code ~ '^([0-9]{2,3}|2A|2B|FR)$'),
    name        TEXT    NOT NULL,
    sha256      TEXT    NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    bytes       INTEGER NOT NULL CHECK (bytes > 0),
    -- The postal codes of the communes of the package, sorted (also in the index).
    postal_codes TEXT[] NOT NULL,
    body_gzip   BYTEA   NOT NULL,
    PRIMARY KEY (import_id, code)
);

CREATE UNLOGGED TABLE elus_staging (
    import_id      BIGINT   NOT NULL,
    -- Where the RNE puts the official: department, collectivity with a special status (69M, 94,
    -- 972...), regional section, or ZX/ZZ for the deputies of overseas collectivities and of the
    -- French abroad. A package gathers one or more areas.
    area           TEXT     NOT NULL,
    -- Index of the mandate in the order of the packages (0 = municipal ... 7 = europeen).
    mandate        SMALLINT NOT NULL CHECK (mandate BETWEEN 0 AND 7),
    territory      TEXT     NOT NULL,
    commune        TEXT,
    name           TEXT     NOT NULL,
    function       TEXT,
    -- Order of the council (0 = mayor or president, n = n-th deputy or vice-president), for sorting only.
    rank           INTEGER  NOT NULL,
    since          TEXT     NOT NULL,
    function_since TEXT
);

CREATE INDEX elus_staging_area_idx ON elus_staging (import_id, area);
CREATE INDEX elus_staging_territory_idx ON elus_staging (import_id, mandate, territory);
