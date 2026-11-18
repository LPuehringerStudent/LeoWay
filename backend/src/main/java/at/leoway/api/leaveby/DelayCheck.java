package at.leoway.api.leaveby;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * F03: does a live delay make the student late? The student's connection is the one the timetable chooses (F02,
 * {@link LeaveByCalculator} without live data). If live data shows it reaching school after the lesson starts, the
 * result says how late and offers an alternative: the latest other connection still in time, or else the one that
 * arrives earliest, if it beats the late one. No live data for the connection means no warning (A03 non-standard).
 */
public final class DelayCheck {
    private DelayCheck() {}

    /**
     * @param chosen the connection the timetable chose, with its live delay
     * @param delayMinutes the live delay shown to the student: at the departure, or at the arrival if only that is
     *                     known
     * @param alternative null if no other connection helps
     */
    public record Late(Connection chosen, int delayMinutes, Instant atSchool, long minutesLate, Alternative alternative) {}

    /** @param inTime true if it reaches school by the lesson start, false if it is only less late */
    public record Alternative(Connection connection, Instant leaveAt, Instant atSchool, boolean inTime) {}

    /**
     * @param live connections with live delays where known (as for the leave-by endpoint)
     * @param now alternatives that can no longer be reached on foot are ignored
     */
    public static Optional<Late> check(Instant lessonStart, LeaveByCalculator.Walks walks, List<Connection> live,
            Instant now) {
        List<Connection> timetable = live.stream().map(c -> c.withDelay(null)).toList();
        // Instant.MIN: the choice is the one the student planned with, even if its departure has passed.
        Optional<Connection> planned = switch (LeaveByCalculator.calculate(lessonStart, walks, timetable, Instant.MIN)) {
            case LeaveBy.InTime t -> Optional.of(t.connection());
            case LeaveBy.NoBuffer t -> Optional.of(t.connection());
            // Late by the timetable already: that is not a delay, the leave-by time says so.
            case LeaveBy.Late t -> Optional.empty();
            case LeaveBy.NoConnection n -> Optional.empty();
        };
        if (planned.isEmpty()) return Optional.empty();
        Connection chosen = live.stream().filter(c -> same(c, planned.get())).findFirst().orElseThrow();
        if (!chosen.predicted()) return Optional.empty();

        Duration toSchool = Duration.ofMinutes(walks.toSchool());
        Instant atSchool = chosen.arrival().plus(toSchool);
        if (!atSchool.isAfter(lessonStart)) return Optional.empty();
        long late = (Duration.between(lessonStart, atSchool).toSeconds() + 59) / 60;
        int delay = chosen.delayMinutes() != null ? chosen.delayMinutes() : chosen.arrivalDelayMinutes();

        List<Connection> others = live.stream().filter(c -> !same(c, chosen)).toList();
        Alternative alternative = switch (LeaveByCalculator.calculate(lessonStart, walks, others, now)) {
            case LeaveBy.InTime t -> new Alternative(t.connection(), t.leaveAt(), t.atSchool(), true);
            case LeaveBy.NoBuffer t -> new Alternative(t.connection(), t.leaveAt(), t.atSchool(), true);
            case LeaveBy.Late t -> t.atSchool().isBefore(atSchool)
                    ? new Alternative(t.connection(), t.leaveAt(), t.atSchool(), false) : null;
            case LeaveBy.NoConnection n -> null;
        };
        return Optional.of(new Late(chosen, delay, atSchool, late, alternative));
    }

    /** Same trip: two vehicles can share a trip id on one day (#77), so the planned departure counts too. */
    static boolean same(Connection a, Connection b) {
        return a.tripId().equals(b.tripId()) && a.plannedDeparture().equals(b.plannedDeparture())
                && a.line().equals(b.line());
    }
}
