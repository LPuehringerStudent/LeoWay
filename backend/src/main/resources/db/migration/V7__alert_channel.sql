-- Alert channels (#62, F04). One row per student and channel; Discord first, push and email follow.
-- The target (for Discord: webhook id and token) is a secret (S01): ciphertext only, never logged or returned.
CREATE TABLE alert_channel (
    student_id       bigint NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    kind             text NOT NULL CHECK (kind IN ('DISCORD')),
    encrypted_target bytea NOT NULL,
    key_version      integer NOT NULL CHECK (key_version > 0),
    created_at       timestamptz NOT NULL DEFAULT now(),
    -- Set when the channel rejected a message for good (webhook deleted or invalid); cleared by saving it again.
    broken_at        timestamptz,
    PRIMARY KEY (student_id, kind)
);
