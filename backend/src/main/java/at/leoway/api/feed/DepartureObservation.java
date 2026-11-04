package at.leoway.api.feed;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One departure as a feed reports it at the moment of polling.
 *
 * @param bay           the stop's bay, or null if the feed has none
 * @param reportedDelay minutes, can be negative; null means the departure has no real-time data
 * @param tripStatus    the feed's own status for the trip, or null
 */
public record DepartureObservation(
        String line,
        String tripId,
        String stopId,
        String bay,
        LocalDate serviceDate,
        Instant plannedAt,
        Integer reportedDelay,
        String tripStatus) {
}
