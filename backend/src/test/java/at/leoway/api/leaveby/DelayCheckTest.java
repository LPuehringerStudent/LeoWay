package at.leoway.api.leaveby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * F03 / A03 with the spec's example: lesson 08:00, 5 min to the stop, 4 min from the stop to school, 3 min buffer, so
 * the latest arrival at the school stop is 07:53. The timetable chooses bus 19 at 07:35 (arrives 07:53); tram 3 at
 * 07:29 arrives 07:45; the next bus 19 at 07:50 arrives 08:08.
 */
class DelayCheckTest {
    static final LocalDate DAY = LocalDate.of(2026, 11, 19);
    static final Instant LESSON = at("08:00");
    static final Instant NOW = at("07:00");
    static final LeaveByCalculator.Walks WALKS = new LeaveByCalculator.Walks(5, 4, 3);

    static Instant at(String time) {
        return DAY.atTime(LocalTime.parse(time)).atZone(LeaveBy.VIENNA).toInstant();
    }

    static Connection bus19(Integer delay) {
        return new Connection("19", "bus-0735", DAY, at("07:35"), at("07:53"), delay);
    }

    static Connection tram3(Integer delay) {
        return new Connection("3", "tram-0729", DAY, at("07:29"), at("07:45"), delay);
    }

    static Connection laterBus19() {
        return new Connection("19", "bus-0750", DAY, at("07:50"), at("08:08"), null);
    }

    @Test
    void sixMinutesOnBus19WarnsAndSuggestsTram3() {
        DelayCheck.Late late = DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(6), laterBus19()), NOW)
                .orElseThrow();
        assertEquals("19", late.chosen().line());
        assertEquals(6, late.delayMinutes());
        assertEquals(at("08:03"), late.atSchool());
        assertEquals(3, late.minutesLate());
        DelayCheck.Alternative a = late.alternative();
        assertEquals("3", a.connection().line());
        assertEquals(at("07:29"), a.connection().departure());
        assertEquals(at("07:24"), a.leaveAt());
        assertEquals(at("07:49"), a.atSchool());
        assertTrue(a.inTime());
    }

    @Test
    void withoutLiveDataThereIsNoWarning() {
        // A03 non-standard: "timetable only", no delay warning.
        assertTrue(DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(null), laterBus19()), NOW).isEmpty());
    }

    @Test
    void aDelayThatStillReachesSchoolInTimeIsNoWarning() {
        // +3: arrives 07:56, at school 08:00, exactly in time. Only the buffer is used up.
        assertTrue(DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(3), laterBus19()), NOW).isEmpty());
        assertTrue(DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(4), laterBus19()), NOW).isPresent());
    }

    @Test
    void anEarlyRunningConnectionIsNoWarning() {
        assertTrue(DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(-1), laterBus19()), NOW).isEmpty());
    }

    @Test
    void lateByTheTimetableAloneIsNoDelayWarning() {
        // Only the 07:50 bus is left in the answer: the leave-by time already says "late".
        assertTrue(DelayCheck.check(LESSON, WALKS, List.of(laterBus19().withDelay(5)), NOW).isEmpty());
    }

    @Test
    void theAlternativeCanBeOnlyLessLate() {
        // Tram 3 is +12 too (at school 08:01), but still beats the bus at +12 (at school 08:09).
        DelayCheck.Late late = DelayCheck.check(LESSON, WALKS, List.of(tram3(12), bus19(12), laterBus19()), NOW)
                .orElseThrow();
        assertEquals(at("08:09"), late.atSchool());
        DelayCheck.Alternative a = late.alternative();
        assertEquals("3", a.connection().line());
        assertEquals(at("08:01"), a.atSchool());
        assertFalse(a.inTime());
    }

    @Test
    void noAlternativeOnceTheOthersHaveLeft() {
        // 07:30: tram 3 has left; the later bus arrives after the late one.
        DelayCheck.Late late = DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(6), laterBus19()),
                at("07:30")).orElseThrow();
        assertNull(late.alternative());
    }

    @Test
    void theChoiceStaysWhenItsDepartureHasPassed() {
        // The student is on the bus at 07:40 when the delay grows: still a warning for that bus.
        DelayCheck.Late late = DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus19(6), laterBus19()),
                at("07:40")).orElseThrow();
        assertEquals("bus-0735", late.chosen().tripId());
    }

    @Test
    void anArrivalDelayAloneCounts() {
        Connection bus = new Connection("3 > 19", "bus-0735", DAY, at("07:35"), at("07:53"), null, 5);
        DelayCheck.Late late = DelayCheck.check(LESSON, WALKS, List.of(tram3(null), bus, laterBus19()), NOW)
                .orElseThrow();
        assertEquals(5, late.delayMinutes());
        assertEquals(2, late.minutesLate());
    }
}
