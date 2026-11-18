package at.leoway.api.alerts;

import at.leoway.api.domain.AlertChannel;
import at.leoway.api.webuntis.KeyCipher;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jboss.logging.Logger;

/**
 * A student's alert channels (#62, F04): set one up with a test message, test it again, remove it, and send alerts
 * to every working channel. A channel that refuses a message for good is marked broken until the student saves it
 * again. Targets are encrypted with {@link KeyCipher} and never logged or returned (S01).
 *
 * <p>HTTP calls to the channels run outside database transactions.
 */
@ApplicationScoped
public class Alerts {
    private static final Logger LOG = Logger.getLogger(Alerts.class);
    static final String TEST_MESSAGE = "LeoWay test message: your alerts for the way to school will arrive here.";
    /** Test messages per student and channel at most this often, so the server can't be used to spam a channel. */
    static final Duration TEST_GAP = Duration.ofSeconds(10);

    /** A test message was sent too recently; try again after {@code retryAfter}. */
    public static class TooSoon extends RuntimeException {
        public final Duration retryAfter;

        TooSoon(Duration retryAfter) {
            super("too soon");
            this.retryAfter = retryAfter;
        }
    }

    /** The student has no such channel. */
    public static class NotSet extends RuntimeException {
        NotSet() {
            super("channel not set");
        }
    }

    /**
     * @param since when the channel was last saved (and its test message sent)
     * @param brokenAt when it refused a message for good, null while it works
     */
    @org.eclipse.microprofile.openapi.annotations.media.Schema(name = "AlertChannelStatus")
    public record Status(AlertChannel.Kind kind, boolean working, Instant since, Instant brokenAt) {
        static Status of(AlertChannel c) {
            return new Status(c.kind, c.brokenAt == null, c.createdAt, c.brokenAt);
        }
    }

    @Inject
    KeyCipher cipher;

    private final Map<AlertChannel.Kind, AlertSender> senders = new EnumMap<>(AlertChannel.Kind.class);
    private final Map<String, Instant> lastTest = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    @Inject
    void senders(Instance<AlertSender> all) {
        all.forEach(s -> senders.put(s.kind(), s));
    }

    /** For tests. A method, because tests reach the bean through a client proxy. */
    void useClock(Clock c) {
        clock = c;
    }

    /** For tests: forget earlier test messages. */
    void resetThrottle() {
        lastTest.clear();
    }

    public List<Status> list(long studentId) {
        return QuarkusTransaction.requiringNew().call(() -> AlertChannel
                .<AlertChannel>list("studentId = ?1 order by kind", studentId).stream().map(Status::of).toList());
    }

    /**
     * Checks the target, sends the test message to it and only then stores it, replacing an older one.
     *
     * @throws AlertSender.InvalidTarget not a usable target, or the channel refused the test message
     * @throws AlertSender.Unavailable the channel can't be reached right now; nothing is stored
     */
    public Status save(long studentId, AlertChannel.Kind kind, String input) {
        AlertSender sender = sender(kind);
        String target = sender.parseTarget(input);
        throttle(studentId, kind);
        try {
            sender.send(target, TEST_MESSAGE);
        } catch (AlertSender.Rejected e) {
            throw new AlertSender.InvalidTarget("Discord did not accept this webhook. Copy the URL again from "
                    + "the channel's Integrations settings.");
        }
        byte[] encrypted = cipher.encrypt(target, studentId, purpose(kind));
        Instant now = clock.instant();
        return QuarkusTransaction.requiringNew().call(() -> {
            AlertChannel c = AlertChannel.find(studentId, kind);
            if (c == null) {
                c = new AlertChannel();
                c.studentId = studentId;
                c.kind = kind;
            }
            c.encryptedTarget = encrypted;
            c.keyVersion = KeyCipher.VERSION;
            c.createdAt = now;
            c.brokenAt = null;
            c.persist();
            return Status.of(c);
        });
    }

    /**
     * Sends the test message again.
     *
     * @throws NotSet no such channel
     * @throws AlertSender.Rejected the channel refused it; it is now marked broken
     */
    public Status test(long studentId, AlertChannel.Kind kind) {
        AlertChannel c = QuarkusTransaction.requiringNew().call(() -> AlertChannel.find(studentId, kind));
        if (c == null) throw new NotSet();
        throttle(studentId, kind);
        deliver(c, TEST_MESSAGE);
        return QuarkusTransaction.requiringNew().call(() -> Status.of(AlertChannel.find(studentId, kind)));
    }

    public void remove(long studentId, AlertChannel.Kind kind) {
        QuarkusTransaction.requiringNew().run(() -> AlertChannel.delete("studentId = ?1 and kind = ?2", studentId, kind));
    }

    /**
     * Sends an alert to every working channel of the student. Failures are logged (student id and reason only) and
     * never stop the other channels. Returns how many channels got it.
     */
    public int notify(long studentId, String message) {
        List<AlertChannel> channels = QuarkusTransaction.requiringNew().call(() -> AlertChannel
                .<AlertChannel>list("studentId = ?1 and brokenAt is null", studentId));
        int sent = 0;
        for (AlertChannel c : channels) {
            try {
                deliver(c, message);
                sent++;
            } catch (AlertSender.Rejected | AlertSender.Unavailable e) {
                LOG.warnf("alert for student %d via %s failed: %s", studentId, c.kind, e.getMessage());
            }
        }
        return sent;
    }

    /** Sends one message; a refusal marks the channel broken. */
    private void deliver(AlertChannel c, String message) {
        AlertSender sender = sender(c.kind);
        String target = cipher.decrypt(c.encryptedTarget, c.studentId, purpose(c.kind));
        try {
            sender.send(target, message);
        } catch (AlertSender.Rejected e) {
            Instant now = clock.instant();
            QuarkusTransaction.requiringNew().run(() -> {
                AlertChannel row = AlertChannel.find(c.studentId, c.kind);
                if (row != null && row.brokenAt == null) row.brokenAt = now;
            });
            throw e;
        }
    }

    private void throttle(long studentId, AlertChannel.Kind kind) {
        Instant now = clock.instant();
        String key = studentId + ":" + kind;
        Instant[] wait = new Instant[1];
        lastTest.compute(key, (k, last) -> {
            if (last != null && now.isBefore(last.plus(TEST_GAP))) {
                wait[0] = last.plus(TEST_GAP);
                return last;
            }
            return now;
        });
        if (wait[0] != null) throw new TooSoon(Duration.between(now, wait[0]));
    }

    private AlertSender sender(AlertChannel.Kind kind) {
        return Optional.ofNullable(senders.get(kind)).orElseThrow(NotSet::new);
    }

    private static String purpose(AlertChannel.Kind kind) {
        return "alert:" + kind;
    }
}
