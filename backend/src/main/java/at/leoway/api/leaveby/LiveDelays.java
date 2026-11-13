package at.leoway.api.leaveby;

import at.leoway.api.domain.DelayRecord;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Adds the latest recorded delay (F09) to connections. A delay counts only if it was collected recently; otherwise
 * the connection stays "timetable only" (A03 non-standard). The delay is read at the home stop, or at the school stop
 * if the recorder does not watch the home stop, and applied to the whole trip.
 */
@ApplicationScoped
public class LiveDelays {
    static final String SOURCE = "linz-ag";
    /** Older than this, a recorded delay is not "live" any more (the feed is STALE after five minutes, #19). */
    static final Duration FRESH = Duration.ofMinutes(5);

    private Clock clock = Clock.systemUTC();

    /** For tests. A method, because tests reach the bean through a client proxy. */
    void useClock(Clock c) {
        clock = c;
    }

    public List<Connection> apply(List<Connection> connections, String homeStopId, String schoolStopId) {
        Instant since = clock.instant().minus(FRESH);
        return connections.stream().map(c -> c.withDelay(latest(c, homeStopId, since)
                .or(() -> latest(c, schoolStopId, since)).orElse(null))).toList();
    }

    private static java.util.Optional<Integer> latest(Connection c, String stopId, Instant since) {
        DelayRecord r = DelayRecord.<DelayRecord>find(
                "source = ?1 and tripId = ?2 and stopId = ?3 and serviceDate = ?4 order by collectedAt desc",
                SOURCE, c.tripId(), stopId, c.serviceDate()).firstResult();
        if (r == null || r.collectedAt.isBefore(since)) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(r.reportedDelay);
    }
}
