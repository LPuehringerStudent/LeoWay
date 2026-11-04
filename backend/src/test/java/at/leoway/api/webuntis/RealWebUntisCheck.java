package at.leoway.api.webuntis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Manual check against the real HTL Leonding WebUntis. Skipped unless UNTIS_USER and UNTIS_SECRET are set,
 * so CI never runs it. Prints counts only, no school data.
 *
 * <pre>
 * set -a; . webuntis-spike/.env; set +a
 * mvn -f backend/pom.xml test -Dtest=RealWebUntisCheck -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "UNTIS_SECRET", matches = ".+")
class RealWebUntisCheck {
    @Test
    void readsOwnWeek() {
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        try (WebUntisClient c = new WebUntisClient(HttpClient.newHttpClient(), new ObjectMapper(),
                URI.create("https://htl-leonding.webuntis.com"), "htl-leonding", Clock.systemUTC())) {
            c.login(System.getenv("UNTIS_USER"), System.getenv("UNTIS_SECRET"));
            var lessons = c.timetable(monday, monday.plusDays(4));
            long days = lessons.stream().map(Lesson::date).distinct().count();
            long firsts = lessons.stream().map(Lesson::date).distinct()
                    .filter(d -> FirstLesson.on(d, lessons).isPresent()).count();
            System.out.printf("REAL CHECK: login ok, %d lessons on %d days, first lesson found on %d days, "
                    + "%d absences in 2 months, %d exams in 4 weeks, timegrid %d days%n",
                    lessons.size(), days, firsts, c.absences(monday.minusMonths(2), monday.plusDays(4)).size(),
                    c.exams(monday, monday.plusWeeks(4)).size(), c.timegrid().size());
            assertTrue(lessons.size() > 0 || days == 0);
        }
    }
}
