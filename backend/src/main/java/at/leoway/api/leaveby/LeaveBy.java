package at.leoway.api.leaveby;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Result of the leave-by calculation (spec 5.4). Times are instants (UTC); {@link #local} converts for display.
 */
public sealed interface LeaveBy {
    ZoneId VIENNA = ZoneId.of("Europe/Vienna");

    /** Where a time comes from, shown next to it (U03). */
    enum Source { TIMETABLE, PREDICTION }

    /** The latest connection that gets the student to school in time. */
    record InTime(Instant leaveAt, Connection connection, Instant atSchool, Source source) implements LeaveBy {}

    /** No connection is in time; this one arrives earliest. {@code minutesLate} counts against the lesson start. */
    record Late(Instant leaveAt, Connection connection, Instant atSchool, Source source, long minutesLate)
            implements LeaveBy {}

    /** No usable connection at all (none given, or all left already). */
    record NoConnection() implements LeaveBy {}

    static LocalTime local(Instant t) {
        return t.atZone(VIENNA).toLocalTime();
    }
}
