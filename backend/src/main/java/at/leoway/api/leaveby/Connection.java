package at.leoway.api.leaveby;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One public-transport trip from the home stop to the school stop, with planned times. Where it comes from (Linz AG
 * trip request or the departure-monitor fallback) is decided in #56.
 *
 * @param tripId the id the recorder stores in {@code delay_record.trip_id}: the {@code AVMSTripID}, or the first id
 *               stored for the departure since #76 (see {@code DelayRecordSink}), so live delays can be found
 * @param serviceDate the trip's operating day; can differ from the calendar date of a departure after midnight
 * @param delayMinutes live delay, null if there is no real-time data ("timetable only", U03)
 */
public record Connection(String line, String tripId, LocalDate serviceDate, Instant plannedDeparture,
        Instant plannedArrival, Integer delayMinutes) {

    public Connection {
        if (plannedArrival.isBefore(plannedDeparture)) throw new IllegalArgumentException("arrival before departure");
    }

    public Connection withDelay(Integer minutes) {
        return new Connection(line, tripId, serviceDate, plannedDeparture, plannedArrival, minutes);
    }

    public boolean predicted() {
        return delayMinutes != null;
    }

    /** Departure as expected now: planned plus live delay, or planned if there is none. */
    public Instant departure() {
        return plannedDeparture.plus(delay());
    }

    public Instant arrival() {
        return plannedArrival.plus(delay());
    }

    private Duration delay() {
        return Duration.ofMinutes(delayMinutes == null ? 0 : delayMinutes);
    }
}
