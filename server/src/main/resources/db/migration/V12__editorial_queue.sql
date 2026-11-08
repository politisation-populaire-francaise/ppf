-- The review queue editorial.<space> of the editorial circuit (docs/integration/circuit-editorial.md,
-- section 5, step 4). Provisional number: renumbered when it enters main (register of the team).
--
-- Personal data (docs/REGISTRE_DONNEES.md): the internal identifier of the team account that decided
-- (pseudonymous) and the message or reason it wrote for the author; no data of a citizen, never an IP
-- address nor a browser. Nothing secret: an approval only marks the version ready to sign, the two-person
-- validation and the signature happen on the pages of the signing service (D-129 revision).

-- The approved state is now « ready to sign »: the signing service holds the approval itself.
ALTER TABLE editorial_draft DROP CONSTRAINT editorial_draft_status_check;
ALTER TABLE editorial_draft ADD CONSTRAINT editorial_draft_status_check
    CHECK (status IN ('draft', 'in_review', 'changes_requested', 'ready_to_sign', 'signing', 'published', 'rejected', 'withdrawn', 'signing_refused'));

-- A draft in the queue always names its submitted version and the moment of the submission.
ALTER TABLE editorial_draft ADD CONSTRAINT editorial_draft_submitted_check
    CHECK (status NOT IN ('in_review', 'ready_to_sign', 'signing', 'signing_refused') OR (submitted_version IS NOT NULL AND submitted_at IS NOT NULL));

DROP INDEX editorial_draft_open_slug_idx;
CREATE UNIQUE INDEX editorial_draft_open_slug_idx ON editorial_draft (slug)
    WHERE slug IS NOT NULL AND status IN ('draft', 'in_review', 'changes_requested', 'ready_to_sign', 'signing', 'signing_refused');
CREATE INDEX editorial_draft_queue_idx ON editorial_draft (space, submitted_at) WHERE status IN ('in_review', 'ready_to_sign');

-- Each review decision on a submitted version. The journal of the base keeps the same decision with
-- identifiers and codes only; the free text (message to the author, reason of a refusal) lives here alone.
CREATE TABLE editorial_review (
    draft_id    UUID        NOT NULL,
    version     INTEGER     NOT NULL,
    reviewer    UUID        NOT NULL REFERENCES team_account (id),
    decision    TEXT        NOT NULL CHECK (decision IN ('changes_requested', 'rejected', 'ready_to_sign')),
    -- Required for a request for changes and a refusal, absent for an approval.
    reason      TEXT        CHECK (reason IS NULL OR length(reason) BETWEEN 1 AND 2000),
    decided_at  TIMESTAMPTZ NOT NULL,
    CHECK ((decision = 'ready_to_sign') = (reason IS NULL)),
    PRIMARY KEY (draft_id, version),
    FOREIGN KEY (draft_id, version) REFERENCES editorial_draft_version (draft_id, version) ON DELETE CASCADE
);

-- A decision is never modified: a new submission is a new version, hence a new decision.
CREATE FUNCTION editorial_review_frozen() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'A review decision is never modified.';
END
$$;

CREATE TRIGGER editorial_review_no_update BEFORE UPDATE ON editorial_review
    FOR EACH ROW EXECUTE FUNCTION editorial_review_frozen();
