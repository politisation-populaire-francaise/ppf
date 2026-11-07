-- Push wake-up subscriptions (M31, docs/integration/notifications.md). A push carries nothing but a
-- constant (`{"v":1}`, encrypted per RFC 8291): it only tells the device to synchronize.
--
-- Two scopes, both off by default on the device:
-- - public: anonymous, no account key; woken when new public content is published (at most once
--   every 30 minutes per subscription);
-- - mailbox: carries the account public key, which the server already knows as the recipient of
--   private messages (`p` tag of kind 1059); woken when a private message arrives for that key (at
--   most once a minute, plus a random delay).
--
-- Nothing else is kept: no client address, no user agent, no interest of any kind. A subscription
-- not renewed for 30 days is deleted by the application (the device renews it at every start).

CREATE TABLE push_subscription (
    -- 128 random bits, base64url without padding: the only thing needed to unsubscribe.
    id          TEXT        PRIMARY KEY CHECK (id ~ '^[A-Za-z0-9_-]{22}$'),
    scope       TEXT        NOT NULL CHECK (scope IN ('public', 'mailbox')),
    -- The push endpoint (an allowed host only, checked by the application).
    endpoint    TEXT        NOT NULL UNIQUE CHECK (length(endpoint) <= 1024),
    -- RFC 8291 keys of the subscription, base64url: P-256 public key (65 bytes) and auth secret (16 bytes).
    p256dh      TEXT        NOT NULL CHECK (length(p256dh) <= 128),
    auth        TEXT        NOT NULL CHECK (length(auth) <= 64),
    -- Lowercase hex account key, for the mailbox scope only (the author of the NIP-98 authorization).
    pubkey      TEXT        CHECK (pubkey ~ '^[0-9a-f]{64}$'),
    created_at  TIMESTAMPTZ NOT NULL,
    renewed_at  TIMESTAMPTZ NOT NULL,
    last_wake_at TIMESTAMPTZ,
    CHECK ((scope = 'mailbox') = (pubkey IS NOT NULL))
);

-- The subscriptions to wake when an envelope arrives for a key.
CREATE INDEX push_subscription_pubkey_idx ON push_subscription (pubkey) WHERE scope = 'mailbox';

-- The public subscriptions due for a wake.
CREATE INDEX push_subscription_public_idx ON push_subscription (last_wake_at) WHERE scope = 'public';

-- The cleanup of subscriptions not renewed for 30 days.
CREATE INDEX push_subscription_renewed_idx ON push_subscription (renewed_at);
