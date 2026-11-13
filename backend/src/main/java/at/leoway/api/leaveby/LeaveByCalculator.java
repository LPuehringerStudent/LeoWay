package at.leoway.api.leaveby;

import at.leoway.api.domain.SavedRoute;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Spec 5.4: choose the latest connection that arrives before the first lesson, allowing the walk to school and the
 * buffer, then subtract the walk to the stop. If none fits, take the one arriving earliest and say how late it is.
 * Works on instants only, so daylight-saving changes and trips after midnight need no special case.
 */
public final class LeaveByCalculator {
    private LeaveByCalculator() {}

    /** Walking and buffer minutes of a saved route. */
    public record Walks(int toStop, int toSchool, int buffer) {
        public static Walks of(SavedRoute r) {
            return new Walks(r.walkToStopMinutes, r.walkToSchoolMinutes, r.bufferMinutes);
        }
    }

    /**
     * @param lessonStart start of the first real lesson
     * @param now connections the student can no longer reach on foot are ignored
     */
    public static LeaveBy calculate(Instant lessonStart, Walks walks, List<Connection> connections, Instant now) {
        Duration toStop = Duration.ofMinutes(walks.toStop());
        Duration toSchool = Duration.ofMinutes(walks.toSchool());
        Instant latestArrival = lessonStart.minus(toSchool).minus(Duration.ofMinutes(walks.buffer()));
        List<Connection> reachable = connections.stream()
                .filter(c -> !c.departure().minus(toStop).isBefore(now)).toList();

        Optional<Connection> inTime = reachable.stream()
                .filter(c -> !c.arrival().isAfter(latestArrival))
                .max(Comparator.comparing(Connection::departure).thenComparing(Connection::arrival));
        if (inTime.isPresent()) {
            Connection c = inTime.get();
            return new LeaveBy.InTime(c.departure().minus(toStop), c, c.arrival().plus(toSchool), source(c));
        }
        // Earliest arrival. Among equal arrivals the latest departure wins: same time at school, less waiting at home.
        return reachable.stream()
                .min(Comparator.comparing(Connection::arrival).thenComparing(Comparator.comparing(Connection::departure).reversed()))
                .<LeaveBy>map(c -> {
                    Instant leaveAt = c.departure().minus(toStop);
                    Instant atSchool = c.arrival().plus(toSchool);
                    if (!atSchool.isAfter(lessonStart)) return new LeaveBy.NoBuffer(leaveAt, c, atSchool, source(c));
                    // Rounded up: 30 seconds late is still late.
                    long late = (Duration.between(lessonStart, atSchool).toSeconds() + 59) / 60;
                    return new LeaveBy.Late(leaveAt, c, atSchool, source(c), late);
                })
                .orElse(new LeaveBy.NoConnection());
    }

    private static LeaveBy.Source source(Connection c) {
        return c.predicted() ? LeaveBy.Source.PREDICTION : LeaveBy.Source.TIMETABLE;
    }
}
