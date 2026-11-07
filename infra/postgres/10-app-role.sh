#!/bin/sh
# Run once by the PostgreSQL image, at the first initialisation of the data volume
# (/docker-entrypoint-initdb.d), as the owner role POSTGRES_USER.
#
# Creates the restricted role ppf_app used by the running server (D-127): it can log in and nothing
# else; its rights on tables come from the Flyway callback afterMigrate.sql, run by the one-shot
# "migrate" service at each deployment (ordinary reads and writes; on the audit log, INSERT and
# SELECT only). It is neither superuser nor owner of any table, so it can never disable a trigger,
# set session_replication_role, or change the schema.
#
# The password is read from a file secret and given to psql as a variable: it never appears on a
# command line, and the database logs no statement (log_statement=none in compose.yml).
set -eu

password="$(cat /run/secrets/app_db_password)"
psql -v ON_ERROR_STOP=1 --no-psqlrc --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set=password="$password" <<'SQL'
SELECT format('CREATE ROLE ppf_app LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS', :'password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ppf_app') \gexec
SQL
