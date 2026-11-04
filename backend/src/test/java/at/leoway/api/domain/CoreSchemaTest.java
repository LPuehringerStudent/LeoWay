package at.leoway.api.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CoreSchemaTest {
    private static final Instant COLLECTED = Instant.parse("2026-11-04T06:00:00Z");

    @Inject
    EntityManager em;

    @Test
    @TestTransaction
    void studentIsStoredWithCreationTime() {
        Student student = student("anna");
        em.clear();

        Student found = Student.findById(student.id);
        assertEquals("anna", found.username);
        assertEquals(student.createdAt.getEpochSecond(), found.createdAt.getEpochSecond());
    }

    @Test
    @TestTransaction
    void credentialKeepsCiphertextBytes() {
        Student student = student("ben");
        WebUntisCredential credential = new WebUntisCredential();
        credential.studentId = student.id;
        credential.webUntisUsername = "ben.untis";
        credential.encryptedAccessKey = new byte[] {1, 2, 3};
        credential.keyVersion = 1;
        credential.persistAndFlush();
        em.clear();

        WebUntisCredential found = WebUntisCredential.findById(student.id);
        assertArrayEquals(new byte[] {1, 2, 3}, found.encryptedAccessKey);
    }

    @Test
    @TestTransaction
    void routeBufferDefaultsToFiveMinutesInTheDatabase() {
        Student student = student("cara");
        em.createNativeQuery("""
                INSERT INTO saved_route
                    (student_id, home_stop_id, school_stop_id, walk_to_stop_minutes, walk_to_school_minutes)
                VALUES (:student, '1', '60501780', 4, 6)
                """).setParameter("student", student.id).executeUpdate();

        SavedRoute found = SavedRoute.find("studentId", student.id).firstResult();
        assertEquals(5, found.bufferMinutes);
    }

    @Test
    @TestTransaction
    void deletingAStudentDeletesTheirPrivateData() {
        Student student = student("dora");
        LatenessEntry entry = new LatenessEntry();
        entry.studentId = student.id;
        entry.lessonDate = LocalDate.of(2026, 11, 4);
        entry.startAt = COLLECTED;
        entry.endAt = COLLECTED.plusSeconds(600);
        entry.persistAndFlush();

        em.createNativeQuery("DELETE FROM student WHERE id = :id").setParameter("id", student.id).executeUpdate();
        em.clear();

        assertEquals(0, LatenessEntry.count("studentId", student.id));
    }

    @Test
    @TestTransaction
    void latenessEntryCannotEndBeforeItStarts() {
        Student student = student("emil");
        LatenessEntry entry = new LatenessEntry();
        entry.studentId = student.id;
        entry.lessonDate = LocalDate.of(2026, 11, 4);
        entry.startAt = COLLECTED;
        entry.endAt = COLLECTED.minusSeconds(60);

        assertThrows(PersistenceException.class, entry::persistAndFlush);
    }

    @Test
    @TestTransaction
    void delayChangesAreAppendedAsNewRows() {
        delayRecord("bay A", COLLECTED, 0).persistAndFlush();
        delayRecord("bay A", COLLECTED.plusSeconds(60), 3).persistAndFlush();
        em.clear();

        List<DelayRecord> rows = DelayRecord.list("tripId = ?1 order by collectedAt", "trip-1");
        assertEquals(List.of(0, 3), rows.stream().map(row -> row.reportedDelay).toList());
    }

    @Test
    @TestTransaction
    void oneTripCanServeTwoBaysOfAStop() {
        delayRecord("bay A", COLLECTED, 0).persistAndFlush();
        delayRecord("bay B", COLLECTED, 0).persistAndFlush();

        assertEquals(2, DelayRecord.count("tripId", "trip-1"));
    }

    @Test
    @TestTransaction
    void sameObservationCannotBeStoredTwice() {
        delayRecord("bay A", COLLECTED, 0).persistAndFlush();
        DelayRecord duplicate = delayRecord("bay A", COLLECTED, 1);

        assertThrows(PersistenceException.class, duplicate::persistAndFlush);
    }

    @Test
    @TestTransaction
    void sameObservationWithoutABayCannotBeStoredTwice() {
        delayRecord(null, COLLECTED, 0).persistAndFlush();
        DelayRecord duplicate = delayRecord(null, COLLECTED, 1);

        assertThrows(PersistenceException.class, duplicate::persistAndFlush);
    }

    @Test
    @TestTransaction
    void delayCanBeNegativeOrMissing() {
        delayRecord("bay A", COLLECTED, -2).persistAndFlush();
        DelayRecord noRealtime = delayRecord("bay A", COLLECTED.plusSeconds(60), null);
        noRealtime.persistAndFlush();
        em.clear();

        DelayRecord found = DelayRecord.findById(noRealtime.id);
        assertNull(found.reportedDelay);
    }

    @Test
    @TestTransaction
    void delayRecordCannotBeUpdated() {
        DelayRecord record = delayRecord("bay A", COLLECTED, 0);
        record.persistAndFlush();

        PersistenceException failure = assertThrows(PersistenceException.class, () -> em
                .createNativeQuery("UPDATE delay_record SET reported_delay = 9 WHERE id = :id")
                .setParameter("id", record.id)
                .executeUpdate());
        assertTrue(rootMessage(failure).contains("cannot be changed"), rootMessage(failure));
    }

    @Test
    @TestTransaction
    void feedStatusRoundTripsItsState() {
        FeedStatus status = new FeedStatus();
        status.feedId = "linz-ag";
        status.state = FeedStatus.State.STALE;
        status.lastSuccessAt = COLLECTED;
        status.persistAndFlush();
        em.clear();

        assertEquals(FeedStatus.State.STALE, FeedStatus.<FeedStatus>findById("linz-ag").state);
    }

    @Test
    @TestTransaction
    void feedStatusRejectsUnknownStates() {
        assertThrows(PersistenceException.class, () -> em.createNativeQuery(
                "INSERT INTO feed_status (feed_id, state) VALUES ('x', 'BROKEN')").executeUpdate());
    }

    @Test
    void schemaHasNoColumnsForDataWeMustNotStore() {
        List<?> forbidden = em.createNativeQuery("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                  AND column_name ~* 'password|address|gps|latitude|longitude|reason|note'
                """).getResultList();

        assertEquals(List.of(), forbidden);
    }

    @Test
    void everyTimestampIsStoredWithATimeZone() {
        List<?> withoutZone = em.createNativeQuery("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                  AND data_type = 'timestamp without time zone'
                """).getResultList();

        assertEquals(List.of(), withoutZone);
    }

    private static Student student(String username) {
        Student student = new Student();
        student.username = username;
        student.persistAndFlush();
        return student;
    }

    private static DelayRecord delayRecord(String bay, Instant collectedAt, Integer delay) {
        DelayRecord record = new DelayRecord();
        record.source = "linz-ag";
        record.line = "19";
        record.tripId = "trip-1";
        record.stopId = "60500120";
        record.bay = bay;
        record.serviceDate = LocalDate.of(2026, 11, 4);
        record.plannedAt = COLLECTED.plusSeconds(900);
        record.reportedDelay = delay;
        record.collectedAt = collectedAt;
        return record;
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
