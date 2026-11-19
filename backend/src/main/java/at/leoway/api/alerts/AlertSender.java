package at.leoway.api.alerts;

import at.leoway.api.domain.AlertChannel;

/**
 * One alert channel (F04, spec: one notification interface, each channel one implementation). Discord first (#62);
 * push and email follow as further implementations.
 */
public interface AlertSender {
    AlertChannel.Kind kind();

    /**
     * Checks what the student pasted and returns the secret to store, normalised. Never echoes the input in the
     * exception message, because it is a secret (S01).
     *
     * @throws InvalidTarget if it isn't a target this channel can use
     */
    String parseTarget(String input);

    /**
     * Sends one message to a stored target.
     *
     * @throws Rejected if the channel refused it for good (target deleted or invalid): the channel is broken
     * @throws Unavailable if it failed for now (rate limit, outage, network): try again later
     */
    void send(String target, String message);

    class InvalidTarget extends RuntimeException {
        public InvalidTarget(String message) {
            super(message);
        }
    }

    class Rejected extends RuntimeException {
        public Rejected(String message) {
            super(message);
        }
    }

    class Unavailable extends RuntimeException {
        public Unavailable(String message) {
            super(message);
        }
    }
}
