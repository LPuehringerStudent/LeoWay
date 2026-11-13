package at.leoway.api.leaveby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import at.leoway.api.domain.DelayRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Live delays come from the recorder (F09); old or missing ones mean "timetable only" (A03). */
@QuarkusTest
class LiveDelaysTest {
    static final Instant NOW = Instant.parse("2026-11-16T06:20:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    static final String HOME = "home-stop-test";
    static final String SCHOOL = "school-stop-test";

    @Inject
    LiveDelays delays;

    @BeforeEach
    void clock() {
        delays.useClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clean() {
        delays.useClock(Clock.systemUTC());
        QuarkusTransaction.requiringNew().run(() -> DelayRecord.delete("stopId in ?1", List.of(HOME, SCHOOL)));
    }

    static void record(String trip, String stop, Integer delay, Instant collected) {
        QuarkusTransaction.requiringNew().run(() -> {
            DelayRecord r = new DelayRecord();
            r.source = "linz-ag";
            r.line = "19";
            r.tripId = trip;
            r.stopId = stop;
            r.serviceDate = DAY;
            r.plannedAt = Instant.parse("2026-11-16T06:31:00Z");
            r.reportedDelay = delay;
            r.collectedAt = collected;
            r.persist();
        });
    }

    static Connection conn(String trip) {
        return new Connection("19", trip, DAY, Instant.parse("2026-11-16T06:31:00Z"),
                Instant.parse("2026-11-16T06:47:00Z"), null);
    }

    @Test
    void latestFreshDelayAtTheHomeStopWins() {
        record("t1", HOME, 2, NOW.minusSeconds(120));
        record("t1", HOME, 6, NOW.minusSeconds(30));
        record("t1", SCHOOL, 9, NOW.minusSeconds(10));
        assertEquals(6, delays.apply(List.of(conn("t1")), HOME, SCHOOL).getFirst().delayMinutes());
    }

    @Test
    void schoolStopIsUsedWhenTheHomeStopIsNotRecorded() {
        record("t2", SCHOOL, 3, NOW.minusSeconds(30));
        assertEquals(3, delays.apply(List.of(conn("t2")), HOME, SCHOOL).getFirst().delayMinutes());
    }

    @Test
    void oldOrMissingDelayMeansTimetableOnly() {
        record("t3", HOME, 4, NOW.minusSeconds(600));
        List<Connection> r = delays.apply(List.of(conn("t3"), conn("unknown")), HOME, SCHOOL);
        assertNull(r.get(0).delayMinutes());
        assertNull(r.get(1).delayMinutes());
    }

    @Test
    void nullDelayFallsThroughToTheSchoolStop() {
        record("t4", HOME, null, NOW.minusSeconds(30));
        record("t4", SCHOOL, 2, NOW.minusSeconds(30));
        assertEquals(2, delays.apply(List.of(conn("t4")), HOME, SCHOOL).getFirst().delayMinutes());
    }

    @Test
    void severalConnectionsAreMatchedByTripAndServiceDate() {
        record("t5", HOME, 1, NOW.minusSeconds(30));
        record("t6", HOME, 7, NOW.minusSeconds(30));
        Connection otherDay = new Connection("19", "t5", DAY.plusDays(1), Instant.parse("2026-11-17T06:31:00Z"),
                Instant.parse("2026-11-17T06:47:00Z"), null);
        List<Connection> r = delays.apply(List.of(conn("t5"), conn("t6"), otherDay), HOME, SCHOOL);
        assertEquals(1, r.get(0).delayMinutes());
        assertEquals(7, r.get(1).delayMinutes());
        assertNull(r.get(2).delayMinutes());
    }
}
