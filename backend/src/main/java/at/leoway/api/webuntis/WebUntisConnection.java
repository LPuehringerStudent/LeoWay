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
        credential.persist();
    }

    public boolean isConnected(long studentId) {
        return WebUntisCredential.findById(studentId) != null;
    }

    /** The first lesson that takes place on {@code date}, or empty (no school, all cancelled, not connected). */
    public Optional<Lesson> firstLesson(long studentId, LocalDate date) {
        return withClient(studentId, c -> FirstLesson.on(date, c.timetable(date, date)));
    }

    /** The next school day's first lesson from {@code from} on, looking up to a week ahead. */
    public Optional<Lesson> nextFirstLesson(long studentId, LocalDate from) {
        return withClient(studentId, c -> {
            List<Lesson> week = c.timetable(from, from.plusDays(7));
            return from.datesUntil(from.plusDays(8))
                    .map(d -> FirstLesson.on(d, week)).flatMap(Optional::stream).findFirst();
        });
    }

    /** Deletes the stored key. The student logs in again to reconnect. */
    @Transactional
    public void disconnect(long studentId) {
        WebUntisCredential.deleteById(studentId);
    }

    public Optional<List<Absence>> absences(long studentId, LocalDate from, LocalDate to) {
        return withClient(studentId, c -> Optional.of(c.absences(from, to)));
    }

    private <T> Optional<T> withClient(long studentId, java.util.function.Function<WebUntisClient, Optional<T>> work) {
        WebUntisCredential credential = WebUntisCredential.findById(studentId);
        if (credential == null) return Optional.empty();
        String key = cipher.decrypt(credential.encryptedAccessKey, studentId);
        try (WebUntisClient client = clients.create()) {
            client.login(credential.webUntisUsername, key);
            return work.apply(client);
        }
    }
}
