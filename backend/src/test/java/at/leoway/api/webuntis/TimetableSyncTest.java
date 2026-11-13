package at.leoway.api.webuntis;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.domain.LatenessEntry;
import at.leoway.api.domain.Student;
import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.security.Roles;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** #59 / A02: background sync of the first lesson, failures per student, lateness entries. */
@QuarkusTest
class TimetableSyncTest {
    static final LocalDate THURSDAY = LocalDate.of(2026, 11, 5);
    static final String SECOND_USER = "second.student";

    @Inject
    TimetableSync sync;

    @Inject
    WebUntisConnection connection;

    @Inject
    FirstLessonChangedRecorder recorder;

    FakeWebUntis fake;
    long a;
    long b;
    final List<String> logs = new CopyOnWriteArrayList<>();
    final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord r) {
            logs.add(r instanceof ExtLogRecord e ? e.getFormattedMessage() : r.getMessage());
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    };

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeWebUntis(18765);
        fake.users.add(SECOND_USER);
        a = student("sync-a");
        b = student("sync-b");
        connection.connect(a, FakeWebUntis.USER, FakeWebUntis.KEY);
        connection.connect(b, SECOND_USER, FakeWebUntis.KEY);
        recorder.events().clear();
        java.util.logging.Logger.getLogger("at.leoway").addHandler(capture);
    }

    @AfterEach
    void tearDown() {
        java.util.logging.Logger.getLogger("at.leoway").removeHandler(capture);
        fake.close();
        QuarkusTransaction.requiringNew().run(() -> Student.delete("username in ?1", List.of("sync-a", "sync-b")));
    }

    static long student(String username) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Student s = new Student();
            s.username = username;
            s.persist();
            return s.id;
        });
    }

    static FirstLessonCache cached(long id) {
        return QuarkusTransaction.requiringNew().call(() -> FirstLessonCache.findById(id));
    }

    static WebUntisCredential credential(long id) {
        return QuarkusTransaction.requiringNew().call(() -> WebUntisCredential.findById(id));
    }

    @Test
    void syncStoresTheFirstRealLesson() {
        assertEquals(new TimetableSync.Result(2, 0), sync.syncAll(THURSDAY));

        FirstLessonCache c = cached(a);
        assertEquals(THURSDAY, c.lessonDate);
        // 08:00 is cancelled in the fake; 08:55 Vienna (CET) is 07:55 UTC.
        assertEquals(Instant.parse("2026-11-05T07:55:00Z"), c.startAt);
        assertEquals(Instant.parse("2026-11-05T08:45:00Z"), c.endAt);
        assertEquals("POS", c.subject);
        assertNotNull(c.syncedAt);
        assertEquals(1, recorder.events().stream().filter(e -> e.studentId() == a).count(), "first sync counts as a change");
    }

    @Test
    void lessonCancelledOvernightMovesTheFirstLesson() {
        sync.syncAll(THURSDAY);
        recorder.events().clear();

        fake.timetable = FakeWebUntis.resource("timetable.json").replace("\"code\":\"irregular\"", "\"code\":\"cancelled\"");
        sync.syncAll(THURSDAY);

        assertEquals(Instant.parse("2026-11-05T09:00:00Z"), cached(a).startAt, "moves to the 10:00 lesson");
        assertTrue(recorder.events().contains(new FirstLessonChanged(a,
                Instant.parse("2026-11-05T07:55:00Z"), Instant.parse("2026-11-05T09:00:00Z"))));
    }

    @Test
    void unchangedLessonFiresNoEvent() {
        sync.syncAll(THURSDAY);
        recorder.events().clear();
        sync.syncAll(THURSDAY);
        assertTrue(recorder.events().isEmpty());
    }

    @Test
    void noLessonInTheNextWeekIsStoredAsEmpty() {
        sync.syncAll(LocalDate.of(2026, 11, 7));
        FirstLessonCache c = cached(a);
        assertNull(c.lessonDate);
        assertNull(c.startAt);
        assertNotNull(c.syncedAt);
    }

    @Test
    void revokedKeyMarksTheConnectionAndOthersStillSync() {
        fake.users.remove(SECOND_USER);

        assertEquals(new TimetableSync.Result(1, 1), sync.syncAll(THURSDAY));
        assertNotNull(cached(a));
        assertNotNull(credential(b).rejectedAt);
        assertNull(cached(b));

        int before = fake.requests.size();
        assertEquals(new TimetableSync.Result(1, 0), sync.syncAll(THURSDAY), "rejected keys are skipped");
        assertFalse(fake.requests.subList(before, fake.requests.size()).stream().anyMatch(r -> r.contains(SECOND_USER)));

        fake.users.add(SECOND_USER);
        connection.connect(b, SECOND_USER, FakeWebUntis.KEY);
        assertNull(credential(b).rejectedAt, "logging in again clears the mark");
    }

    @Test
    void unreachableWebUntisFailsEveryStudentWithoutMarkingKeys() {
        fake.close();
        assertEquals(new TimetableSync.Result(0, 2), sync.syncAll(THURSDAY));
        assertNull(credential(a).rejectedAt);
        assertNull(credential(b).rejectedAt);
    }

    @Test
    void logsCarryNoKeysOrSchoolData() {
        sync.syncAll(THURSDAY);
        fake.users.remove(SECOND_USER);
        sync.syncAll(THURSDAY);
        sync.syncLateness(LocalDate.of(2026, 11, 4));

        assertTrue(logs.stream().anyMatch(l -> l.contains("student " + b + " failed: KEY_REJECTED")), logs.toString());
        for (String secret : List.of(FakeWebUntis.KEY, FakeWebUntis.USER, SECOND_USER, "POS", "Programmieren",
                "SECRET", "sync-a", "sync-b")) {
            assertTrue(logs.stream().noneMatch(l -> l.contains(secret)), secret + " in " + logs);
        }
    }

    @Test
    void latenessInTheFirstLessonIsStoredOnce() {
        LocalDate wednesday = LocalDate.of(2026, 11, 4);
        fake.timetable = """
                {"result":[{"id":9,"date":20261104,"startTime":800,"endTime":850,"su":[{"name":"D"}]},
                           {"id":10,"date":20261104,"startTime":900,"endTime":950,"su":[{"name":"E"}]}]}""";
        fake.absences = """
                {"data":{"absences":[
                  {"id":1,"startDate":20261104,"endDate":20261104,"startTime":800,"endTime":810},
                  {"id":2,"startDate":20261104,"endDate":20261104,"startTime":800,"endTime":1200},
                  {"id":3,"startDate":20261104,"endDate":20261104,"startTime":900,"endTime":910}]}}""";

        sync.syncLateness(wednesday);
        sync.syncLateness(wednesday);

        List<LatenessEntry> entries = QuarkusTransaction.requiringNew().call(() -> LatenessEntry.list("studentId", a));
        assertEquals(1, entries.size(), "only 08:00-08:10; the whole-morning absence and the later one don't match");
        assertEquals(Instant.parse("2026-11-04T07:00:00Z"), entries.getFirst().startAt);
        assertEquals(Instant.parse("2026-11-04T07:10:00Z"), entries.getFirst().endAt);
    }

    @Test
    @TestSecurity(user = "sync-b", roles = Roles.STUDENT)
    void statusAsksToReconnectAfterARejectedKey() {
        fake.users.remove(SECOND_USER);
        sync.syncAll(THURSDAY);
        given().get("/api/me/webuntis").then().statusCode(200)
                .body("connected", equalTo(true))
                .body("needsReconnect", equalTo(true));
    }

    @Test
    @TestSecurity(user = "sync-a", roles = Roles.STUDENT)
    void statusIsServedFromTheSync() {
        given().get("/api/me/webuntis").then().statusCode(200)
                .body("needsReconnect", equalTo(false))
                .body("syncedAt", notNullValue());
        assertNotNull(cached(a), "an outdated or missing cache is filled on the first request");

        int before = fake.requests.size();
        given().get("/api/me/webuntis").then().statusCode(200);
        assertEquals(before, fake.requests.size(), "second request comes from the cache");
    }

    @Test
    void parallelFirstSyncsStoreOneRowWithoutFailing() throws Exception {
        // #81: the endpoint's on-demand sync and a scheduled run can meet on a cold cache.
        for (int round = 0; round < 10; round++) {
            QuarkusTransaction.requiringNew().run(() -> FirstLessonCache.deleteById(a));
            var start = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
            List<java.util.concurrent.Future<?>> runs = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                runs.add(pool.submit(() -> {
                    start.await();
                    return sync.sync(a, THURSDAY);
                }));
            }
            start.countDown();
            for (var r : runs) r.get(); // throws if one sync failed
            pool.shutdown();
            assertEquals(Instant.parse("2026-11-05T07:55:00Z"), cached(a).startAt);
        }
    }
}
