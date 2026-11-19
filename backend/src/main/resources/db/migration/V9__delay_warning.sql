-- Delay warnings (#63, F03, A03).

-- True if an exam from the WebUntis exam call (#2) overlaps the first lesson. Set by the timetable sync.
ALTER TABLE first_lesson ADD COLUMN exam boolean NOT NULL DEFAULT false;

-- The current delay warning per student, for the next first lesson only, replaced when the lesson changes. A row
-- with resolved_at set means the connection is back in time; Home no longer shows it. Goes away with the student.
CREATE TABLE delay_warning (
    student_id            bigint PRIMARY KEY REFERENCES student (id) ON DELETE CASCADE,
    lesson_start          timestamptz NOT NULL,
    -- The connection the timetable chose (F02) and how late it runs now
    line                  text NOT NULL,
    trip_id               text NOT NULL,
    planned_departure     timestamptz NOT NULL,
    delay_minutes         integer NOT NULL,
    at_school             timestamptz NOT NULL,
    minutes_late          integer NOT NULL CHECK (minutes_late > 0),
    exam                  boolean NOT NULL,
    -- Another connection that still arrives in time, or arrives earlier; all NULL if there is none
    alternative_line      text,
    alternative_departure timestamptz,
    alternative_leave_at  timestamptz,
    alternative_at_school timestamptz,
    -- When the last message for this warning went out (one per change, not per poll)
    sent_at               timestamptz NOT NULL,
    resolved_at           timestamptz,
    CHECK ((alternative_line IS NULL) = (alternative_departure IS NULL)
        AND (alternative_departure IS NULL) = (alternative_leave_at IS NULL)
        AND (alternative_leave_at IS NULL) = (alternative_at_school IS NULL))
);
