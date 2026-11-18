package at.leoway.api.warnings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.alerts.Alerts;
import at.leoway.api.alerts.FakeDiscord;
import at.leoway.api.domain.AlertChannel;
import at.leoway.api.domain.DelayRecord;
import at.leoway.api.domain.DelayWarning;
import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.domain.SavedRoute;
import at.leoway.api.domain.Student;
import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.feed.LinzAgAdapter;
import at.leoway.api.leaveby.LeaveBy;
import at.leoway.api.leaveby.LinzAgTripsAccess;
import at.leoway.api.stops.FakeLinzAg;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * #63 / A03 against the fake Linz AG trip request (Hauptbahnhof to Limesstraße on 19.11.2026: 07:09-07:21 "3 > 19",
 * 07:16-07:30 "4 > 191", 07:24-07:36 "3 > 11", 07:39-07:51 "3 > 19" live +3) and the fake Discord. Lesson 07:44,
 * walks 7 / 3 min, 5 min buffer: the timetable chooses the 07:24, arriving 07:36. +6 min on it means school at 07:45,
 * one minute late; the 07:16 still arrives in time.
 */
@QuarkusTest
class DelayWarningsTest {
    static final LocalDate THURSDAY = LocalDate.of(2026, 11, 19);
    static final Instant MORNING = THURSDAY.atTime(6, 30).atZone(LeaveBy.VIENNA).toInstant();
    static final String HOME = "60501720";
    static final String SCHOOL = "60501780";
    static final String WEBHOOK = "https://discord.com/api/webhooks/1234567890123456780/" + "wQ3_-".repeat(13) + "xyz";

    FakeLinzAg linzAg;
    FakeDiscord discord;
    long studentId;
    String tripId;

    @Inject
    DelayWarnings warnings;

    @Inject
    LinzAgTripsAccess trips;

    @Inject
    Alerts alerts;

    @BeforeEach
    void seed() throws Exception {
        linzAg = new FakeLinzAg(18766);
        discord = new FakeDiscord(18767);
        trips.reset(Clock.fixed(MORNING, ZoneOffset.UTC));
        warnings.useClock(Clock.fixed(MORNING, ZoneOffset.UTC));
        tripId = LinzAgTripsAccess.parseFixtureTripId(2);
        studentId = QuarkusTransaction.requiringNew().call(() -> {
            Student s = new Student();
            s.username = "wanda";
            s.persist();
            WebUntisCredential c = new WebUntisCredential();
            c.studentId = s.id;
            c.webUntisUsername = "wanda";
            c.encryptedAccessKey = new byte[] {1};
            c.keyVersion = 1;
            c.persist();
            SavedRoute r = new SavedRoute();
            r.studentId = s.id;
            r.homeStopId = HOME;
            r.schoolStopId = SCHOOL;
            r.walkToStopMinutes = 7;
            r.walkToSchoolMinutes = 3;
            r.bufferMinutes = 5;
            r.persist();
            return s.id;
        });
        lesson("07:44", false);
        alerts.save(studentId, AlertChannel.Kind.DISCORD, WEBHOOK);
        discord.posts.clear(); // the test message
    }

    @AfterEach
    void clean() {
        linzAg.close();
        discord.close();
        warnings.useClock(Clock.systemUTC());
        QuarkusTransaction.requiringNew().run(() -> {
            Student.delete("username", "wanda");
            DelayRecord.delete("stopId in ?1", List.of(HOME, SCHOOL));
        });
    }

    @Test
    void sixMinutesLateWarnsOnceWithAnAlternative() {
        delay(6);
        assertEquals(new DelayWarnings.Result(1, 1, 0), warnings.checkAll());

        assertEquals(1, discord.posts.size());
        String message = discord.posts.getFirst().body();
        assertTrue(message.contains("3 > 11 at 07:24 is 6 min late"), message);
        assertTrue(message.contains("reach school at 07:45, 1 min after your first lesson starts (07:44)"), message);
        assertTrue(message.contains("Take 4 > 191 at 07:16 instead: leave home at 07:09"), message);
        assertFalse(message.contains("exam"), message);

        DelayWarning w = warning();
        assertEquals("3 > 11", w.line);
        assertEquals(6, w.delayMinutes);
        assertEquals(1, w.minutesLate);
        assertEquals("4 > 191", w.alternativeLine);
        assertNull(w.resolvedAt);

        // The same delay on the next polls: no second message (one per change, not per poll).
        assertEquals(new DelayWarnings.Result(1, 0, 0), warnings.checkAll());
        assertEquals(1, discord.posts.size());
    }

    @Test
    void anExamInTheFirstLessonIsMentioned() {
        lesson("07:44", true);
        delay(6);
        warnings.checkAll();
        assertTrue(discord.posts.getFirst().body().contains("There is an exam in your first lesson."));
        assertTrue(warning().exam);
    }

    @Test
    void withoutLiveDataThereIsNoWarning() {
        // A03 non-standard: the 07:24 has no live data in the trip answer and none was recorded.
        assertEquals(new DelayWarnings.Result(1, 0, 0), warnings.checkAll());
        assertTrue(discord.posts.isEmpty());
        assertNull(warning());
    }

    @Test
    void aDelayThatKeepsTheStudentInTimeIsNoWarning() {
        delay(4); // school at 07:43
        warnings.checkAll();
        assertTrue(discord.posts.isEmpty());
        assertNull(warning());
    }

    @Test
    void aMuchBiggerDelayIsANewMessageAndRecoveryResolvesIt() {
        delay(6);
        warnings.checkAll();
        delay(7); // one minute more: not worth another message
        warnings.checkAll();
        assertEquals(1, discord.posts.size());
        assertEquals(2, warning().minutesLate, "Home still shows the latest numbers");

        delay(10);
        warnings.checkAll();
        assertEquals(2, discord.posts.size());
        assertTrue(discord.posts.getLast().body().contains("is 10 min late"));

        delay(0);
        warnings.checkAll();
        assertNotNull(warning().resolvedAt);
        assertEquals(2, discord.posts.size());

        delay(6); // late again after being in time: a new change
        warnings.checkAll();
        assertEquals(3, discord.posts.size());
        assertNull(warning().resolvedAt);
    }

    @Test
    void lessonsOutsideTheWindowAreNotChecked() {
        lesson("10:00", false); // more than three hours ahead: no live data yet
        delay(6);
        assertEquals(new DelayWarnings.Result(0, 0, 0), warnings.checkAll());
        assertTrue(linzAg.queries.isEmpty());
    }

    @Test
    void aRejectedWebUntisKeyIsSkipped() {
        QuarkusTransaction.requiringNew().run(() -> {
            WebUntisCredential c = WebUntisCredential.findById(studentId);
            c.rejectedAt = MORNING;
        });
        delay(6);
        warnings.checkAll();
        assertTrue(discord.posts.isEmpty());
    }

    @Test
    void theSourceBeingDownSkipsTheStudentWithoutAWarning() {
        // The fake answers HTTP 500 for the origin "fail".
        QuarkusTransaction.requiringNew().run(() -> SavedRoute.update("homeStopId = 'fail' where studentId = ?1", studentId));
        delay(6);
        assertEquals(new DelayWarnings.Result(1, 0, 1), warnings.checkAll());
        assertNull(warning());
    }

    private void lesson(String start, boolean exam) {
        QuarkusTransaction.requiringNew().run(() -> {
            FirstLessonCache c = FirstLessonCache.findById(studentId);
            if (c == null) {
                c = new FirstLessonCache();
                c.studentId = studentId;
            }
            c.lessonDate = THURSDAY;
            c.startAt = THURSDAY.atTime(LocalTime.parse(start)).atZone(LeaveBy.VIENNA).toInstant();
            c.endAt = c.startAt.plusSeconds(50 * 60);
            c.subject = "SYP";
            c.exam = exam;
            c.syncedAt = MORNING;
            c.persist();
        });
    }

    /** The recorder sees the 07:24 at the home stop with this delay, just now. */
    private void delay(int minutes) {
        QuarkusTransaction.requiringNew().run(() -> {
            DelayRecord r = new DelayRecord();
            r.source = LinzAgAdapter.ID;
            r.line = "3";
            r.tripId = tripId;
            r.stopId = HOME;
            r.serviceDate = THURSDAY;
            r.plannedAt = THURSDAY.atTime(7, 24).atZone(LeaveBy.VIENNA).toInstant();
            r.reportedDelay = minutes;
            r.collectedAt = Instant.now();
            r.persist();
        });
    }

    private DelayWarning warning() {
        return QuarkusTransaction.requiringNew().call(() -> DelayWarning.findById(studentId));
    }
}
