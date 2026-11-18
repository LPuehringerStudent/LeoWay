package at.leoway.api.leaveby;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One public-transport trip from the home stop to the school stop, with planned times. It can have changes; then
 * {@code line} lists every line ("3 > 19") and the times are those of the first departure and the last arrival.
 *
 * @param tripId the id of the first ride as the recorder stores it in {@code delay_record.trip_id}: the
 *               {@code AVMSTripID}, or {@code stateless#key} (see {@code DelayRecordSink}), so live delays can be found
 * @param serviceDate the trip's operating day; can differ from the calendar date of a departure after midnight
 * @param delayMinutes live delay at the departure, null if there is no real-time data ("timetable only", U03)
 * @param arrivalDelayMinutes live delay at the arrival, null if unknown. With a change, a late first ride can still
 *                            reach the same last ride, so this can differ from the departure delay.
 */
public record Connection(String line, String tripId, LocalDate serviceDate, Instant plannedDeparture,
        Instant plannedArrival, Integer delayMinutes, Integer arrivalDelayMinutes) {

    public Connection {
        if (plannedArrival.isBefore(plannedDeparture)) throw new IllegalArgumentException("arrival before departure");
    }

    /** One delay for the whole trip (a single ride, or a delay read from {@code delay_record}). */
    public Connection(String line, String tripId, LocalDate serviceDate, Instant plannedDeparture,
            Instant plannedArrival, Integer delayMinutes) {
        this(line, tripId, serviceDate, plannedDeparture, plannedArrival, delayMinutes, delayMinutes);
    }

    public Connection withDelay(Integer minutes) {
        return new Connection(line, tripId, serviceDate, plannedDeparture, plannedArrival, minutes, minutes);
    }

    public boolean predicted() {
        return delayMinutes != null || arrivalDelayMinutes != null;
    }

    /** Departure as expected now: planned plus live delay, or planned if there is none. */
    public Instant departure() {
        return plannedDeparture.plus(minutes(delayMinutes));
    }

    /** Never before the expected departure, even if the arrival is reported earlier than that. */
    public Instant arrival() {
        Instant arrival = plannedArrival.plus(minutes(arrivalDelayMinutes));
        return arrival.isBefore(departure()) ? departure() : arrival;
    }

    private static Duration minutes(Integer m) {
        return Duration.ofMinutes(m == null ? 0 : m);
    }
}
