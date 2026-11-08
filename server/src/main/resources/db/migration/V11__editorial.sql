-- The drafts of the editorial circuit (docs/integration/circuit-editorial.md, sections 4 and 7, step 3).
-- Provisional number: renumbered when it enters main (register of the team).
--
-- Personal data (docs/REGISTRE_DONNEES.md): the editorial texts written by the team and the internal
-- identifiers of the team accounts that wrote them (pseudonymous); no data of a citizen, never an IP
-- address nor a browser. The queue (step 4), the review decisions and the signing requests (step 5) add
-- their own tables in their own migrations.

-- One editorial content being written or updated (one row per draft, whatever its versions).
CREATE TABLE editorial_draft (
    id                 UUID        PRIMARY KEY,
    space              TEXT        NOT NULL CHECK (space IN ('comprendre', 'voir')),
    -- ppf-type of the content (ArticleType.tagValue), fixed at creation.
    type               TEXT        NOT NULL CHECK (type ~ '^[a-z][a-z-]{1,39}$'),
    -- The slug of the latest version when it is valid (null while the editor has not typed a valid one).
    slug               TEXT        CHECK (slug IS NULL OR slug ~ '^[a-z0-9][a-z0-9-]{0,99}$'),
    -- The title of the latest version, for the lists (at most 200 characters, as the engine checks).
    title              TEXT        NOT NULL CHECK (length(title) <= 200),
    -- The published event this draft updates (null: a new content).
    base_event_id      TEXT        CHECK (base_event_id IS NULL OR base_event_id ~ '^[0-9a-f]{64}$'),
    -- Every state of the design; only 'draft' is used before the queue (step 4).
    status             TEXT        NOT NULL CHECK (status IN ('draft', 'in_review', 'changes_requested', 'approved', 'signing', 'published', 'rejected', 'withdrawn', 'signing_refused')),
    author_account     UUID        NOT NULL REFERENCES team_account (id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    latest_version     INTEGER     NOT NULL CHECK (latest_version >= 1),
    submitted_version  INTEGER     CHECK (submitted_version IS NULL OR submitted_version BETWEEN 1 AND latest_version),
    submitted_at       TIMESTAMPTZ,
    published_event_id TEXT        CHECK (published_event_id IS NULL OR published_event_id ~ '^[0-9a-f]{64}$')
);

-- One open draft per address: a slug names one content of the editorial key, whatever its type.
CREATE UNIQUE INDEX editorial_draft_open_slug_idx ON editorial_draft (slug)
    WHERE slug IS NOT NULL AND status IN ('draft', 'in_review', 'changes_requested', 'approved', 'signing', 'signing_refused');
CREATE INDEX editorial_draft_space_idx ON editorial_draft (space, updated_at DESC);

-- The successive versions of a draft: never modified (trigger), deleted only with their draft.
CREATE TABLE editorial_draft_version (
    draft_id     UUID        NOT NULL REFERENCES editorial_draft (id) ON DELETE CASCADE,
    version      INTEGER     NOT NULL CHECK (version >= 1),
    -- Ordered [[key, value], ...] of the front matter, repeated keys kept, exactly as a file of content/.
    entries      JSONB       NOT NULL CHECK (jsonb_typeof(entries) = 'array'),
    body         TEXT        NOT NULL CHECK (length(body) <= 60000),
    -- The answer to « produced with the help of an AI? »; null: not answered yet (never a default).
    origin       TEXT        CHECK (origin IS NULL OR origin IN ('human', 'ai-assisted')),
    -- For an update of a published content: correction (an error of substance) or update, and its note.
    change_kind  TEXT        CHECK (change_kind IS NULL OR change_kind IN ('correction', 'mise-a-jour')),
    change_note  TEXT        CHECK (change_note IS NULL OR length(change_note) <= 1000),
    written_by   UUID        NOT NULL REFERENCES team_account (id),
    written_at   TIMESTAMPTZ NOT NULL,
    -- How many problems EditorialEngine.check found when the version was saved.
    problems     INTEGER     NOT NULL CHECK (problems >= 0),
    PRIMARY KEY (draft_id, version)
);

CREATE FUNCTION editorial_draft_version_frozen() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'A version of an editorial draft is never modified: save a new version.';
END
$$;

CREATE TRIGGER editorial_draft_version_no_update BEFORE UPDATE ON editorial_draft_version
    FOR EACH ROW EXECUTE FUNCTION editorial_draft_version_frozen();
