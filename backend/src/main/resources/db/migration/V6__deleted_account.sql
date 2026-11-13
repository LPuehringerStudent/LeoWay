-- Accounts deleted by their student (#55, L02, A13). Only the id, no username: this list must not become a record
-- of who used LeoWay. deploy/restore.sh copies it from the live database into a restored backup and deletes these
-- students again, so a deleted account does not come back with an older backup.
CREATE TABLE deleted_account (
    student_id bigint PRIMARY KEY,
    deleted_at timestamptz NOT NULL DEFAULT now()
);
