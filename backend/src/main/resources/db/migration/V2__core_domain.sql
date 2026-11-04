-- Core domain tables (spec 5.3). All timestamps are timestamptz and stored in UTC (spec 5.4).
-- Never add columns for passwords, home addresses, GPS, absence reasons or notes (L02).

CREATE TABLE student (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username   text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT now()
);

-- The key is ciphertext only. The encryption key stays outside the database and repository (S01).
CREATE TABLE webuntis_credential (
    student_id           bigint PRIMARY KEY REFERENCES student (id) ON DELETE CASCADE,
    webuntis_username    text NOT NULL,
    encrypted_access_key bytea NOT NULL,
    key_version          integer NOT NULL CHECK (key_version > 0)
);

CREATE TABLE saved_route (
    id                     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id             bigint NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    home_stop_id           text NOT NULL,
    school_stop_id         text NOT NULL,
    walk_to_stop_minutes   integer NOT NULL CHECK (walk_to_stop_minutes >= 0),
    walk_to_school_minutes integer NOT NULL CHECK (walk_to_school_minutes >= 0),
    buffer_minutes         integer NOT NULL DEFAULT 5 CHECK (buffer_minutes >= 0)
);
CREATE INDEX saved_route_student_idx ON saved_route (student_id);

CREATE TABLE lateness_entry (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id  bigint NOT NULL REFERENCES student (id) ON DELETE CASCADE,
    lesson_date date NOT NULL,
    start_at    timestamptz NOT NULL,
    end_at      timestamptz NOT NULL,
    CHECK (end_at >= start_at)
);
CREATE INDEX lateness_entry_student_idx ON lateness_entry (student_id, lesson_date);

-- A departure's delay keeps changing until it leaves, so the recorder appends a row per change
-- instead of updating one. The latest row holds the last prediction (Linz AG spike, #3 and #18).
CREATE TABLE delay_record (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source         text NOT NULL,
    line           text NOT NULL,
    trip_id        text NOT NULL,
    stop_id        text NOT NULL,
    bay            text,
    service_date   date NOT NULL,
    planned_at     timestamptz NOT NULL,
    reported_delay integer,          -- minutes, can be negative; NULL means no real-time data
    trip_status    text,             -- as sent by the feed, e.g. MONITORED or EXTRA_TRIP|MONITORED
    collected_at   timestamptz NOT NULL,
    -- One trip can serve two bays of a stop. NULLS NOT DISTINCT keeps the key working without a bay.
    CONSTRAINT delay_record_observation_key UNIQUE NULLS NOT DISTINCT
        (source, trip_id, stop_id, bay, service_date, collected_at)
);

CREATE FUNCTION reject_delay_record_update() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'delay_record rows cannot be changed after collection (S04)';
END;
$$;

CREATE TRIGGER delay_record_immutable
    BEFORE UPDATE ON delay_record
    FOR EACH ROW EXECUTE FUNCTION reject_delay_record_update();

CREATE TABLE feed_status (
    feed_id         text PRIMARY KEY,
    state           text NOT NULL CHECK (state IN ('OK', 'STALE', 'DOWN')),
    last_success_at timestamptz,
    last_error_at   timestamptz,
    last_error      text
);
