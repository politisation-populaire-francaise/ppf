-- Flyway callback, run after every migration (D-127): the rights of the restricted role of the server.
--
-- The role is named by the placeholder appRole (PPF_DATABASE_APP_ROLE, `ppf_app` in infra/); it is
-- created by the PostgreSQL container, never here. Empty (development, tests) or absent: nothing is done.
-- Idempotent: every right is granted or revoked again at each run, new tables included.
--
-- The server may read and write the data, but on the team journal only read and append: no UPDATE,
-- DELETE or TRUNCATE, and, not owning the table, it can neither disable its trigger nor change the
-- schema; it reads the history of the migrations without changing it. The envelopes of the cases
-- (contact_envelope) are read, added and deleted, never updated.
--
-- The legal register (schema legal_register, D-077) is write-only for the server: INSERT of the columns it
-- fills (never the reception date nor the identifier, set by the database), USAGE of the schema and of its
-- sequences, never SELECT, UPDATE, DELETE or TRUNCATE; reading and purging only through the SECURITY
-- DEFINER functions answer_requisition (a requisition approved by two people), purge (nothing younger
-- than one year) and requisitions_by_month (the public counter).

DO $$
DECLARE
    app_role TEXT := '${appRole}';
BEGIN
    IF app_role = '' OR NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        RETURN;
    END IF;
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', current_schema(), app_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA %I TO %I', current_schema(), app_role);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA %I TO %I', current_schema(), app_role);
    IF to_regclass('team_audit_log') IS NOT NULL THEN
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON team_audit_log FROM %I', app_role);
    END IF;
    -- The envelopes of the cases (M30) are never rewritten: added, then deleted by the retention only.
    IF to_regclass('contact_envelope') IS NOT NULL THEN
        EXECUTE format('REVOKE UPDATE, TRUNCATE ON contact_envelope FROM %I', app_role);
    END IF;
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        EXECUTE format('REVOKE ALL ON flyway_schema_history FROM %I', app_role);
        EXECUTE format('GRANT SELECT ON flyway_schema_history TO %I', app_role);
    END IF;
    IF to_regnamespace('legal_register') IS NOT NULL THEN
        -- Revoking a right on a table also revokes it on each of its columns: the grants below start from nothing.
        EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA legal_register FROM %I', app_role);
        EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA legal_register FROM %I', app_role);
        EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA legal_register FROM %I', app_role);
        EXECUTE format('GRANT USAGE ON SCHEMA legal_register TO %I', app_role);
        EXECUTE format('GRANT USAGE ON ALL SEQUENCES IN SCHEMA legal_register TO %I', app_role);
        EXECUTE format(
            'GRANT INSERT (event_id, operation, created_at, kind, pubkey, protocol, removed_event_id, removed_created_at, removed_pubkey) '
            'ON legal_register.content_operation TO %I', app_role);
        EXECUTE format('GRANT INSERT (pubkey, name, display_name, event_id, event_created_at) ON legal_register.account_pseudonym TO %I', app_role);
        EXECUTE format(
            'GRANT EXECUTE ON FUNCTION legal_register.purge(), legal_register.answer_requisition(UUID), '
            'legal_register.requisitions_by_month() TO %I', app_role);
    END IF;
END
$$;
