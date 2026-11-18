package at.leoway.api.webuntis;

import at.leoway.api.domain.WebUntisCredential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Connects a student's WebUntis account (F01) and reads their timetable with the stored key.
 * The key is only stored after WebUntis accepted it; a rejected key leaves nothing behind.
 */
@ApplicationScoped
public class WebUntisConnection {
    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(WebUntisConnection.class);

    @Inject
    WebUntisClientFactory clients;

    @Inject
    KeyCipher cipher;

    /** Checks the key with a real login, then stores it encrypted. Throws KEY_REJECTED otherwise. */
    @Transactional
    public void connect(long studentId, String username, String accessKey) {
        try (WebUntisClient client = clients.create()) {
            client.login(username, accessKey);
        }
        WebUntisCredential credential = WebUntisCredential.findById(studentId);
        if (credential == null) {
            credential = new WebUntisCredential();
            credential.studentId = studentId;
        }
        credential.webUntisUsername = username;
        credential.encryptedAccessKey = cipher.encrypt(accessKey, studentId);
        credential.keyVersion = KeyCipher.VERSION;
        credential.rejectedAt = null;
        credential.persist();
    }

    public boolean isConnected(long studentId) {
        return WebUntisCredential.findById(studentId) != null;
    }

    /** The first lesson that takes place on {@code date}, or empty (no school, all cancelled, not connected). */
    public Optional<Lesson> firstLesson(long studentId, LocalDate date) {
        return withClient(studentId, c -> FirstLesson.on(date, c.timetable(date, date)));
    }

    /**
     * The next school day's first lesson from {@code from} on, looking up to a week ahead, and whether an exam
     * overlaps it (#63). If only the exam call fails, the lesson still counts, without an exam.
     */
    public Optional<FirstLesson.Found> nextFirstLesson(long studentId, LocalDate from) {
        return withClient(studentId, c -> {
            List<Lesson> week = c.timetable(from, from.plusDays(7));
            Optional<Lesson> first = from.datesUntil(from.plusDays(8))
                    .map(d -> FirstLesson.on(d, week)).flatMap(Optional::stream).findFirst();
            return first.map(l -> new FirstLesson.Found(l, FirstLesson.isExam(l, exams(c, l.date()))));
        });
    }

    private static List<Exam> exams(WebUntisClient c, LocalDate day) {
        try {
            return c.exams(day, day);
        } catch (WebUntisException e) {
            if (e.reason() == WebUntisException.Reason.KEY_REJECTED) throw e;
            LOG.warnf("exam call failed: %s", e.reason());
            return List.of();
        }
    }

    /** Deletes the stored key. The student logs in again to reconnect. */
    @Transactional
    public void disconnect(long studentId) {
        WebUntisCredential.deleteById(studentId);
    }

    public Optional<List<Absence>> absences(long studentId, LocalDate from, LocalDate to) {
        return withClient(studentId, c -> Optional.of(c.absences(from, to)));
    }

    /** Logs in with the stored key and runs {@code work}. Empty if the student has no WebUntis connection. */
    <T> Optional<T> withClient(long studentId, java.util.function.Function<WebUntisClient, Optional<T>> work) {
        WebUntisCredential credential = WebUntisCredential.findById(studentId);
        if (credential == null) return Optional.empty();
        String key = cipher.decrypt(credential.encryptedAccessKey, studentId);
        try (WebUntisClient client = clients.create()) {
            client.login(credential.webUntisUsername, key);
            return work.apply(client);
        }
    }
}
