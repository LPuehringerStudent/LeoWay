-- Background timetable sync (#59, F02, A02).

-- Set when WebUntis no longer accepts the stored key (revoked or changed). The sync skips the student and the app
-- asks them to log in again; a successful login clears it.
ALTER TABLE webuntis_credential ADD COLUMN rejected_at timestamptz;

-- The next first real lesson per connected student, replaced on every sync (L03). All lesson columns are NULL when
-- no lesson was found in the next week. Goes away with the WebUntis connection.
CREATE TABLE first_lesson (
    student_id  bigint PRIMARY KEY REFERENCES webuntis_credential (student_id) ON DELETE CASCADE,
    lesson_date date,
    start_at    timestamptz,
    end_at      timestamptz,
    subject     text,
    synced_at   timestamptz NOT NULL,
    CHECK ((lesson_date IS NULL) = (start_at IS NULL) AND (start_at IS NULL) = (end_at IS NULL)),
    CHECK (end_at >= start_at)
);
