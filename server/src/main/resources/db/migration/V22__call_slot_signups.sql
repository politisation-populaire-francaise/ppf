-- The signups to the slots of calls (AP-11, docs/integration/agir-creneaux.md). Each row is an envelope (NIP-59)
-- readable by the author of the call only, sealed by a one-time key: the server knows the call and the slot (to
-- count the places), the hash of the token that withdraws the signup, and when to erase it (two days after the end
-- of the slot). No date of signup, no address, no account: nothing ties a row to a person.

CREATE TABLE call_slot_signup (
    id TEXT PRIMARY KEY,
    call_address TEXT NOT NULL,
    slot_id TEXT NOT NULL,
    envelope TEXT NOT NULL,
    withdraw_hash TEXT NOT NULL,
    expires_at BIGINT NOT NULL
);

CREATE INDEX call_slot_signup_slot ON call_slot_signup (call_address, slot_id);
CREATE INDEX call_slot_signup_expiry ON call_slot_signup (expires_at);
