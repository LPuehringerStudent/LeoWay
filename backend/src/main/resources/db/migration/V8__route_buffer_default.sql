-- Apply the route editor's suggested buffer to future inserts only.
-- Existing students' saved buffer choices are kept.
ALTER TABLE saved_route ALTER COLUMN buffer_minutes SET DEFAULT 10;
