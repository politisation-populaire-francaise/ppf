-- Accounts declared as collectives (D-133, docs/integration/types-de-compte.md): they never vote,
-- and stay collectives. Public keys only; the profile itself is a public signed event.
-- Provisional number (Communauté branch, after V16 push and V17 group roles), renumbered at the entry into main.
CREATE TABLE account_type (
    pubkey TEXT PRIMARY KEY CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    type TEXT NOT NULL CHECK (type IN ('collective')),
    declared_at BIGINT NOT NULL
);
