package at.leoway.api.webuntis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.webuntis.WebUntisException.Reason;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WebUntisClientTest {
    static final LocalDate THU = LocalDate.of(2026, 11, 5);
    FakeWebUntis fake;
    WebUntisClient client;

    @BeforeEach
    void start() throws Exception {
        fake = new FakeWebUntis(0);
        client = new WebUntisClient(HttpClient.newHttpClient(), new ObjectMapper(), URI.create(fake.baseUrl()),
                "htl-leonding", Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        fake.close();
    }

    @Test
    void logsInWithOneTimeCodeNotTheKey() {
        client.login(FakeWebUntis.USER, FakeWebUntis.KEY);
        String login = fake.requests.getFirst();
        assertTrue(login.contains("getUserData2017"));
        assertFalse(login.contains(FakeWebUntis.KEY), "the access key itself must never be sent");
        assertTrue(login.contains("school=htl-leonding"));
    }

    @Test
    void rejectedKeyGivesKeyRejected() {
        var e = assertThrows(WebUntisException.class, () -> client.login("someone.else", FakeWebUntis.KEY));
        assertEquals(Reason.KEY_REJECTED, e.reason());
        var invalid = assertThrows(WebUntisException.class, () -> client.login(FakeWebUntis.USER, "not base32!"));
        assertEquals(Reason.KEY_REJECTED, invalid.reason());
    }

    @Test
    void readsTimetableAndFindsFirstRealLesson() {
        client.login(FakeWebUntis.USER, FakeWebUntis.KEY);
        List<Lesson> lessons = client.timetable(THU, THU.plusDays(1));
        assertEquals(4, lessons.size());
        assertEquals(Lesson.Status.CANCELLED, lessons.getFirst().status());
        assertTrue(fake.requests.stream().anyMatch(r -> r.contains("\"id\":4711") && r.contains("\"type\":5")));

        // 08:00 is cancelled, the irregular 08:55 lesson counts.
        Lesson first = FirstLesson.on(THU, lessons).orElseThrow();
        assertEquals(LocalTime.of(8, 55), first.start());
        assertEquals("POS", first.subject());
        // Friday: everything cancelled.
        assertTrue(FirstLesson.on(THU.plusDays(1), lessons).isEmpty());
    }

    @Test
    void absencesKeepOnlyDateAndTime() {
        client.login(FakeWebUntis.USER, FakeWebUntis.KEY);
        List<Absence> absences = client.absences(THU.minusDays(7), THU);
        assertEquals(List.of(new Absence(LocalDate.of(2026, 11, 4), LocalTime.of(8, 0), LocalTime.of(8, 10))), absences);
        assertFalse(absences.toString().contains("SECRET"));
    }

    @Test
    void examsAreRequestedWithoutGrades() {
        client.login(FakeWebUntis.USER, FakeWebUntis.KEY);
        List<Exam> exams = client.exams(THU, THU.plusDays(7));
        assertEquals(new Exam(LocalDate.of(2026, 11, 10), LocalTime.of(10, 0), LocalTime.of(10, 50), "AM"), exams.getFirst());
        assertTrue(fake.requests.stream().anyMatch(r -> r.contains("/api/exams") && r.contains("withGrades=false")));
    }

    @Test
    void onlyAllowListedCallsExist() {
        assertEquals(java.util.Set.of("getUserData2017", "getTimetable", "getTimegridUnits", "logout"),
                WebUntisClient.ALLOWED_RPC);
        assertTrue(WebUntisClient.ALLOWED_REST.stream().noneMatch(p -> p.contains("message") || p.contains("homework")
                || p.contains("grade")));
    }

    @Test
    void closeLogsOut() {
        client.login(FakeWebUntis.USER, FakeWebUntis.KEY);
        client.close();
        assertTrue(fake.requests.getLast().contains("\"logout\""));
        assertThrows(IllegalStateException.class, () -> client.timetable(THU, THU));
    }

    @Test
    void unreachableServerIsUnavailable() {
        fake.close();
        var e = assertThrows(WebUntisException.class, () -> client.login(FakeWebUntis.USER, FakeWebUntis.KEY));
        assertEquals(Reason.UNAVAILABLE, e.reason());
    }
}
