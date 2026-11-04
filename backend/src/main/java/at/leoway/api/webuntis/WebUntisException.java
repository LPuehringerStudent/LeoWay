package at.leoway.api.webuntis;

/** A WebUntis call failed. Messages never contain keys, session ids or school data (S01). */
public class WebUntisException extends RuntimeException {
    public enum Reason { KEY_REJECTED, UNAVAILABLE, FORBIDDEN_CALL, BAD_RESPONSE }

    private final Reason reason;

    public WebUntisException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
