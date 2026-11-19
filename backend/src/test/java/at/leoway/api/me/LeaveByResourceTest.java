package at.leoway.api.me;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.DelayRecord;
import at.leoway.api.domain.DelayWarning;
import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.domain.SavedRoute;
import at.leoway.api.domain.Student;
import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.feed.LinzAgAdapter;
import at.leoway.api.leaveby.LeaveBy;
import at.leoway.api.leaveby.LinzAgTripsAccess;
import at.leoway.api.security.Roles;
import at.leoway.api.stops.FakeLinzAg;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GET /api/me/leave-by (#58, A02) against the fake Linz AG trip request. It is the evening before; the trips are
 * Hauptbahnhof to Limesstraße on 19.11.2026 (see LinzAgTripsTest): 07:09-07:21, 07:16-07:30, 07:24-07:36, and
 * 07:39-07:51 running live 3 min late, arriving 07:53. Walks: 7 min to the stop, 3 to school, 5 min buffer.
 */
@QuarkusTest
@TestSecurity(user = "anna", roles = Roles.STUDENT)
class LeaveByResourceTest {
    static final LocalDate THURSDAY = LocalDate.of(2026, 11, 19);
    static final Instant EVENING_BEFORE = LocalDate.of(2026, 11, 18).atTime(20, 0).atZone(LeaveBy.VIENNA).toInstant();
    static final String HOME = "60501720";
    static final String SCHOOL = "60501780";

    FakeLinzAg fake;
    long annaId;
    long routeId;

    @Inject
    LeaveByResource resource;

    @Inject
    LinzAgTripsAccess trips;

    @BeforeEach
    void seed() throws Exception {
        fake = new FakeLinzAg(18766);
        trips.reset(Clock.fixed(EVENING_BEFORE, ZoneOffset.UTC));
        resource.useClock(Clock.fixed(EVENING_BEFORE, ZoneOffset.UTC));
        QuarkusTransaction.requiringNew().run(() -> {
            annaId = student("anna").id;
            student("ben");
            connect(annaId);
            lesson(annaId, "08:00");
            routeId = route(annaId, HOME).id;
        });
    }

    @AfterEach
    void clean() {
        fake.close();
        resource.useClock(Clock.systemUTC());
        QuarkusTransaction.requiringNew().run(() -> {
            Student.delete("username in ('anna', 'ben')");
            DelayRecord.delete("stopId in ?1", java.util.List.of(HOME, SCHOOL));
        });
    }

    @Test
    void standardCaseTakesTheLatestConnectionInTime() {
        // A02: lesson 08:00, arrive by 07:52. The 07:39 runs late and arrives 07:53, so the 07:24 it is.
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("status", equalTo("IN_TIME"))
                .body("routeId", equalTo((int) routeId))
                .body("lessonStart", equalTo("2026-11-19T08:00:00+01:00"))
                .body("leaveAt", equalTo("2026-11-19T07:17:00+01:00"))
                .body("atSchool", equalTo("2026-11-19T07:39:00+01:00"))
                .body("connection.line", equalTo("3 > 11"))
                .body("connection.departure", equalTo("2026-11-19T07:24:00+01:00"))
                .body("source", equalTo("TIMETABLE"))
                .body("minutesLate", nullValue());
    }

    @Test
    void liveDataIsMarkedAsPrediction() {
        // Lesson 08:05, arrive by 07:57: the late 07:39 (now 07:42, arriving 07:53) still fits.
        update(() -> lesson(annaId, "08:05"));
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("status", equalTo("IN_TIME"))
                .body("leaveAt", equalTo("2026-11-19T07:35:00+01:00"))
                .body("connection.plannedDeparture", equalTo("2026-11-19T07:39:00+01:00"))
                .body("connection.departure", equalTo("2026-11-19T07:42:00+01:00"))
                .body("connection.arrival", equalTo("2026-11-19T07:53:00+01:00"))
                .body("source", equalTo("PREDICTION"));
    }

    @Test
    void recordedDelayIsUsedWhenTheTripRequestHasNoLiveData() {
        // The recorder saw the 07:24 four minutes late at the home stop just now.
        String tripId = at.leoway.api.leaveby.LinzAgTripsAccess.parseFixtureTripId(2);
        QuarkusTransaction.requiringNew().run(() -> {
            DelayRecord r = new DelayRecord();
            r.source = LinzAgAdapter.ID;
            r.line = "3";
            r.tripId = tripId;
            r.stopId = HOME;
            r.serviceDate = THURSDAY;
            r.plannedAt = THURSDAY.atTime(7, 24).atZone(LeaveBy.VIENNA).toInstant();
            r.reportedDelay = 4;
            r.collectedAt = Instant.now();
            r.persist();
        });
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("status", equalTo("IN_TIME"))
                .body("connection.departure", equalTo("2026-11-19T07:28:00+01:00"))
                .body("leaveAt", equalTo("2026-11-19T07:21:00+01:00"))
                .body("source", equalTo("PREDICTION"));
    }

    @Test
    void anOpenDelayWarningIsShownForTheFirstRoute() {
        // Stored by the background check (#63): the 07:39 is late, take the 07:24.
        Instant lessonStart = THURSDAY.atTime(8, 0).atZone(LeaveBy.VIENNA).toInstant();
        QuarkusTransaction.requiringNew().run(() -> {
            DelayWarning w = new DelayWarning();
            w.studentId = annaId;
            w.lessonStart = lessonStart;
            w.line = "3 > 19";
            w.tripId = "t";
            w.plannedDeparture = THURSDAY.atTime(7, 39).atZone(LeaveBy.VIENNA).toInstant();
            w.delayMinutes = 9;
            w.atSchool = THURSDAY.atTime(8, 3).atZone(LeaveBy.VIENNA).toInstant();
            w.minutesLate = 3;
            w.exam = true;
            w.alternativeLine = "3 > 11";
            w.alternativeDeparture = THURSDAY.atTime(7, 24).atZone(LeaveBy.VIENNA).toInstant();
            w.alternativeLeaveAt = THURSDAY.atTime(7, 17).atZone(LeaveBy.VIENNA).toInstant();
            w.alternativeAtSchool = THURSDAY.atTime(7, 39).atZone(LeaveBy.VIENNA).toInstant();
            w.sentAt = EVENING_BEFORE;
            w.persist();
        });
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("warning.line", equalTo("3 > 19"))
                .body("warning.plannedDeparture", equalTo("2026-11-19T07:39:00+01:00"))
                .body("warning.delayMinutes", equalTo(9))
                .body("warning.atSchool", equalTo("2026-11-19T08:03:00+01:00"))
                .body("warning.minutesLate", equalTo(3))
                .body("warning.exam", equalTo(true))
                .body("warning.alternative.line", equalTo("3 > 11"))
                .body("warning.alternative.leaveAt", equalTo("2026-11-19T07:17:00+01:00"))
                .body("warning.alternative.inTime", equalTo(true));
        given().queryParam("routeId", routeId).get("/api/me/leave-by").then().statusCode(200)
                .body("warning", nullValue());

        update(() -> DelayWarning.update("resolvedAt = ?1 where studentId = ?2", EVENING_BEFORE, annaId));
        given().get("/api/me/leave-by").then().statusCode(200).body("warning", nullValue());
    }

    @Test
    void nothingInTimeShowsTheEarliestArrival() {
        // Lesson 07:20: the 07:09 arrives 07:21, at school 07:24.
        update(() -> lesson(annaId, "07:20"));
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("status", equalTo("LATE"))
                .body("leaveAt", equalTo("2026-11-19T07:02:00+01:00"))
                .body("atSchool", equalTo("2026-11-19T07:24:00+01:00"))
                .body("minutesLate", equalTo(4));
    }

    @Test
    void asksForTheArrivalAtTheSchoolStopAndCachesTheAnswer() {
        given().get("/api/me/leave-by").then().statusCode(200);
        given().get("/api/me/leave-by").then().statusCode(200);
        assertEquals(1, fake.queries.size(), "second call served from the cache");
        String q = fake.queries.getFirst();
        // 08:00 minus 3 min walk to school.
        assertTrue(q.contains("itdDate=20261119") && q.contains("itdTime=0757")
                && q.contains("itdTripDateTimeDepArr=arr") && q.contains("name_origin=" + HOME)
                && q.contains("name_destination=" + SCHOOL), q);
    }

    @Test
    void anotherStudentsRouteLooksMissing() {
        long bens = QuarkusTransaction.requiringNew().call(() -> route(Student.<Student>find("username", "ben")
                .firstResult().id, HOME).id);
        given().queryParam("routeId", bens).get("/api/me/leave-by").then().statusCode(404);
        given().queryParam("routeId", routeId).get("/api/me/leave-by").then().statusCode(200);
    }

    @Test
    void nothingToCalculate() {
        update(() -> SavedRoute.delete("studentId", annaId));
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("status", equalTo("NO_ROUTE"))
                .body("lessonStart", equalTo("2026-11-19T08:00:00+01:00"));

        update(() -> {
            FirstLessonCache c = FirstLessonCache.findById(annaId);
            c.lessonDate = null;
            c.startAt = null;
            c.endAt = null;
        });
        given().get("/api/me/leave-by").then().statusCode(200).body("status", equalTo("NO_LESSON"));

        update(() -> WebUntisCredential.<WebUntisCredential>findById(annaId).rejectedAt = EVENING_BEFORE);
        given().get("/api/me/leave-by").then().statusCode(200).body("status", equalTo("NEEDS_RECONNECT"));

        update(() -> WebUntisCredential.deleteById(annaId));
        given().get("/api/me/leave-by").then().statusCode(200).body("status", equalTo("NOT_CONNECTED"));
        assertEquals(0, fake.queries.size(), "nothing asked of Linz AG");
    }

    @Test
    void sourceDownIs503() {
        update(() -> SavedRoute.<SavedRoute>findById(routeId).homeStopId = "fail");
        given().get("/api/me/leave-by").then().statusCode(503);
        update(() -> SavedRoute.<SavedRoute>findById(routeId).homeStopId = "broken");
        given().get("/api/me/leave-by").then().statusCode(503);
    }

    @Test
    void whileTheGapRunsAnExpiredAnswerIsServedAndOnlyNewTripsWait() {
        // #99 review: Home refreshes every minute, so expired answers must not turn into 503s.
        trips.useMinGap(java.time.Duration.ofSeconds(15));
        given().get("/api/me/leave-by").then().statusCode(200);

        // Six hours later the 08:00 answer has expired. Another lesson start asks Linz AG and starts the gap.
        Instant later = EVENING_BEFORE.plus(java.time.Duration.ofHours(6)).plusSeconds(60);
        at(later);
        update(() -> lesson(annaId, "08:05"));
        given().get("/api/me/leave-by").then().statusCode(200);
        assertEquals(2, fake.queries.size());

        at(later.plusSeconds(1));
        update(() -> lesson(annaId, "08:00"));
        given().get("/api/me/leave-by").then().statusCode(200)
                .body("leaveAt", equalTo("2026-11-19T07:17:00+01:00"));
        assertEquals(2, fake.queries.size(), "expired answer served, no request");

        at(later.plusSeconds(2));
        update(() -> lesson(annaId, "08:10"));
        given().get("/api/me/leave-by").then().statusCode(503).header("Retry-After", "13");
        assertEquals(2, fake.queries.size());
    }

    private void at(Instant t) {
        trips.useClock(Clock.fixed(t, ZoneOffset.UTC));
        resource.useClock(Clock.fixed(t, ZoneOffset.UTC));
    }

    private static void update(Runnable r) {
        QuarkusTransaction.requiringNew().run(r);
    }

    private static Student student(String username) {
        Student s = new Student();
        s.username = username;
        s.persist();
        return s;
    }

    private static void connect(long studentId) {
        WebUntisCredential c = new WebUntisCredential();
        c.studentId = studentId;
        c.webUntisUsername = "anna";
        c.encryptedAccessKey = new byte[] {1};
        c.keyVersion = 1;
        c.persist();
    }

    private static void lesson(long studentId, String start) {
        FirstLessonCache c = FirstLessonCache.findById(studentId);
        if (c == null) {
            c = new FirstLessonCache();
            c.studentId = studentId;
        }
        c.lessonDate = THURSDAY;
        c.startAt = THURSDAY.atTime(LocalTime.parse(start)).atZone(LeaveBy.VIENNA).toInstant();
        c.endAt = c.startAt.plusSeconds(50 * 60);
        c.subject = "SYP";
        c.syncedAt = EVENING_BEFORE;
        c.persist();
    }

    private static SavedRoute route(long studentId, String home) {
        SavedRoute r = new SavedRoute();
        r.studentId = studentId;
        r.homeStopId = home;
        r.schoolStopId = SCHOOL;
        r.walkToStopMinutes = 7;
        r.walkToSchoolMinutes = 3;
        r.bufferMinutes = 5;
        r.persist();
        return r;
    }
}
