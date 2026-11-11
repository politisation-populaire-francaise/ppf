-- « Signaler ce contenu » (S-38, D-234): a report is a case of M30 (ticket / report). For this subtype only, the
-- address of the content reported (already public) and the code of the reason are kept in clear, so that the back
-- office groups the reports by content before the team client exists. The comment stays encrypted in the envelope;
-- the server never learns who reported. Retention: that of the tickets (deleted 3 years after closing).
ALTER TABLE contact_case
    ADD COLUMN report_content TEXT CHECK (report_content IS NULL OR length(report_content) <= 300),
    ADD COLUMN report_reason  TEXT CHECK (report_reason IS NULL OR report_reason IN ('illicite', 'haine', 'donnees-personnelles', 'tromperie', 'spam', 'autre')),
    ADD CONSTRAINT contact_case_report_only CHECK ((report_content IS NULL AND report_reason IS NULL) OR subtype = 'report');

CREATE INDEX contact_case_report_content ON contact_case (report_content) WHERE report_content IS NOT NULL;
