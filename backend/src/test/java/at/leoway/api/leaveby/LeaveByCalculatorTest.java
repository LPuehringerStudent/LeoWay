package at.leoway.api.leaveby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import at.leoway.api.leaveby.LeaveBy.InTime;
import at.leoway.api.leaveby.LeaveBy.Late;
import at.leoway.api.leaveby.LeaveByCalculator.Walks;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 5.4 and A02. Times are written in Vienna time and converted, like the timetable would be. */
class LeaveByCalculatorTest {
    static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);
    /** Spec example (bus 07:31, leave by 07:24): 7 min to the stop, 3 min to school, 5 min buffer. */
    static final Walks WALKS = new Walks(7, 3, 5);

    static Instant at(LocalDate d, String hhmm) {
        return d.atTime(LocalTime.parse(hhmm)).atZone(LeaveBy.VIENNA).toInstant();
    }

    static Connection bus(LocalDate d, String dep, String arr) {
        return new Connection("19", "19-" + dep, d, at(d, dep), at(d, arr), null);
    }

    static LeaveBy calc(LocalDate d, String lesson, List<Connection> cs) {
        return LeaveByCalculator.calculate(at(d, lesson), WALKS, cs, at(d, "05:00"));
    }

    static String local(Instant t) {
        return LeaveBy.local(t).toString();
    }

    @Test
    void normalDayTakesTheLatestConnectionInTime() {
        // Spec screen: first lesson 08:00, bus 19 07:31 -> 07:47, leave by 07:24.
        var r = assertInstanceOf(InTime.class, calc(MONDAY, "08:00", List.of(
                bus(MONDAY, "07:16", "07:32"), bus(MONDAY, "07:31", "07:47"), bus(MONDAY, "07:46", "08:02"))));
        assertEquals("07:24", local(r.leaveAt()));
        assertEquals("07:31", local(r.connection().departure()));
        assertEquals("07:50", local(r.atSchool()));
        assertEquals(LeaveBy.Source.TIMETABLE, r.source());
    }

    @Test
    void arrivingExactlyAtTheLimitStillFits() {
        // 08:00 - 3 min walk - 5 min buffer = 07:52.
        var r = assertInstanceOf(InTime.class, calc(MONDAY, "08:00", List.of(bus(MONDAY, "07:36", "07:52"))));
        assertEquals("07:29", local(r.leaveAt()));
    }

    @Test
    void cancelledFirstLessonMovesTheLeaveByTime() {
        // A02 non-standard, spec screen: 08:00 cancelled, first lesson 08:55, leave by 08:16.
        List<Connection> cs = List.of(bus(MONDAY, "07:31", "07:47"), bus(MONDAY, "08:23", "08:39"),
                bus(MONDAY, "08:35", "08:51"));
        assertEquals("07:24", local(assertInstanceOf(InTime.class, calc(MONDAY, "08:00", cs)).leaveAt()));
        assertEquals("08:16", local(assertInstanceOf(InTime.class, calc(MONDAY, "08:55", cs)).leaveAt()));
    }

    @Test
    void nothingFitsShowsTheEarliestArrival() {
        var r = assertInstanceOf(Late.class, calc(MONDAY, "08:00", List.of(
                bus(MONDAY, "07:56", "08:12"), bus(MONDAY, "07:41", "07:57"))));
        assertEquals("07:41", local(r.connection().departure()));
        assertEquals("08:00", local(r.atSchool()));
        assertEquals(0, r.minutesLate(), "arrives on time but eats the buffer");

        var later = assertInstanceOf(Late.class, calc(MONDAY, "08:00", List.of(bus(MONDAY, "07:56", "08:12"))));
        assertEquals(15, later.minutesLate());
    }

    @Test
    void noConnectionsGivesNoConnection() {
        assertInstanceOf(LeaveBy.NoConnection.class, calc(MONDAY, "08:00", List.of()));
    }

    @Test
    void connectionsThatCanNoLongerBeReachedAreIgnored() {
        List<Connection> cs = List.of(bus(MONDAY, "07:31", "07:47"), bus(MONDAY, "07:46", "08:02"));
        var r = LeaveByCalculator.calculate(at(MONDAY, "08:00"), WALKS, cs, at(MONDAY, "07:28"));
        assertEquals("07:46", local(assertInstanceOf(Late.class, r).connection().departure()));
    }

    @Test
    void liveDelayIsUsedAndMarkedAsPrediction() {
        // Bus 19 +6 min: 07:37 -> 07:53 misses 07:52, so the earlier bus is chosen.
        List<Connection> cs = List.of(bus(MONDAY, "07:16", "07:32").withDelay(0),
                bus(MONDAY, "07:31", "07:47").withDelay(6));
        var r = assertInstanceOf(InTime.class, calc(MONDAY, "08:00", cs));
        assertEquals("07:16", local(r.connection().departure()));
        assertEquals(LeaveBy.Source.PREDICTION, r.source());

        var early = assertInstanceOf(InTime.class, calc(MONDAY, "08:00", List.of(bus(MONDAY, "07:46", "08:02").withDelay(-10))));
        assertEquals("07:29", local(early.leaveAt()), "an early bus moves the leave-by time forward");
    }

    @Test
    void springForwardDay() {
        // Clocks go 02:00 -> 03:00 on 29 March 2026; Monday after, and a lesson on the day itself.
        LocalDate monday = LocalDate.of(2026, 3, 30);
        var r = assertInstanceOf(InTime.class, calc(monday, "08:00", List.of(bus(monday, "07:31", "07:47"))));
        assertEquals("07:24", local(r.leaveAt()));
        assertEquals(Instant.parse("2026-03-30T05:24:00Z"), r.leaveAt(), "CEST is UTC+2");

        LocalDate sunday = LocalDate.of(2026, 3, 29);
        var night = LeaveByCalculator.calculate(at(sunday, "03:30"), WALKS,
                List.of(bus(sunday, "01:50", "03:10")), at(sunday, "00:00"));
        // 01:50 CET to 03:10 CEST is only 20 real minutes; walking 7 min before 01:50 is 01:43.
        assertEquals("01:43", local(assertInstanceOf(InTime.class, night).leaveAt()));
    }

    @Test
    void fallBackDay() {
        // Clocks go 03:00 -> 02:00 on 25 October 2026.
        LocalDate monday = LocalDate.of(2026, 10, 26);
        var r = assertInstanceOf(InTime.class, calc(monday, "08:00", List.of(bus(monday, "07:31", "07:47"))));
        assertEquals(Instant.parse("2026-10-26T06:24:00Z"), r.leaveAt(), "CET is UTC+1");

        LocalDate sunday = LocalDate.of(2026, 10, 25);
        Instant lesson = Instant.parse("2026-10-25T02:30:00Z"); // 03:30 CET
        Instant dep = Instant.parse("2026-10-25T00:10:00Z");    // first 02:10, still CEST
        Connection c = new Connection("N", "n", sunday, dep, Instant.parse("2026-10-25T01:40:00Z"), null);
        var night = assertInstanceOf(InTime.class,
                LeaveByCalculator.calculate(lesson, WALKS, List.of(c), Instant.parse("2026-10-24T22:00:00Z")));
        assertEquals(Instant.parse("2026-10-25T00:03:00Z"), night.leaveAt(), "instants, not wall-clock arithmetic");
    }

    @Test
    void departureBeforeMidnightKeepsItsServiceDate() {
        // A night trip of Sunday's service leaves 23:50 and arrives 00:20 on Monday.
        LocalDate sunday = MONDAY.minusDays(1);
        Connection night = new Connection("N82", "n82", sunday, at(sunday, "23:50"), at(MONDAY, "00:20"), null);
        var r = assertInstanceOf(InTime.class, LeaveByCalculator.calculate(at(MONDAY, "00:40"), WALKS,
                List.of(night), at(sunday, "22:00")));
        assertEquals("23:43", local(r.leaveAt()));
        assertEquals(sunday, r.leaveAt().atZone(LeaveBy.VIENNA).toLocalDate());
        assertEquals(sunday, r.connection().serviceDate());
    }
}
