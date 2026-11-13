-- The recorder looks up the trip id already stored for a departure when Linz AG sends one without AVMSTripID (#69).
CREATE INDEX delay_record_departure_idx ON delay_record (stop_id, planned_at, line);
