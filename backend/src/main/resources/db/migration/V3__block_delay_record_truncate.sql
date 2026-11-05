-- Delay records cannot be changed after collection (S04). The trigger in V2 only stops row updates.
-- DELETE stays allowed for retention (L03).
CREATE FUNCTION reject_delay_record_truncate() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'delay_record cannot be truncated (S04)';
END;
$$;

CREATE TRIGGER delay_record_no_truncate
    BEFORE TRUNCATE ON delay_record
    FOR EACH STATEMENT EXECUTE FUNCTION reject_delay_record_truncate();
