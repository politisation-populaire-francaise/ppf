-- Flyway callback, run after every migration (D-127): the rights of the restricted role of the server.
--
-- The role is named by the placeholder appRole (PPF_DATABASE_APP_ROLE, `ppf_app` in infra/); it is
-- created by the PostgreSQL container, never here. Empty (development, tests) or absent: nothing is done.
-- Idempotent: every right is granted or revoked again at each run, new tables included.
--
-- The server may read and write the data, but on the team journal only read and append: no UPDATE,
-- DELETE or TRUNCATE, and, not owning the table, it can neither disable its trigger nor change the
-- schema; it reads the history of the migrations without changing it.

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
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        EXECUTE format('REVOKE ALL ON flyway_schema_history FROM %I', app_role);
        EXECUTE format('GRANT SELECT ON flyway_schema_history TO %I', app_role);
    END IF;
END
$$;
