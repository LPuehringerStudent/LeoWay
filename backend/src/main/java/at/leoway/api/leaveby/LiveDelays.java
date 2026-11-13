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
    static final String SOURCE = at.leoway.api.feed.LinzAgAdapter.ID;
    /** Older than this, a recorded delay is not "live" any more (the feed is STALE after five minutes, #19). */
    static final Duration FRESH = Duration.ofMinutes(5);

    private Clock clock = Clock.systemUTC();

    /** For tests. A method, because tests reach the bean through a client proxy. */
    void useClock(Clock c) {
        clock = c;
    }

    public List<Connection> apply(List<Connection> connections, String homeStopId, String schoolStopId) {
        if (connections.isEmpty()) return connections;
        Instant since = clock.instant().minus(FRESH);
        // One query for all connections (#81). Only rows collected recently, newest first per trip and stop.
        List<DelayRecord> rows = DelayRecord.<DelayRecord>find(
                "source = ?1 and tripId in ?2 and stopId in ?3 and collectedAt >= ?4 order by collectedAt desc",
                SOURCE, connections.stream().map(Connection::tripId).distinct().toList(),
                List.of(homeStopId, schoolStopId), since).list();
        record Key(String tripId, String stopId, java.time.LocalDate serviceDate) {}
        java.util.Map<Key, DelayRecord> latest = new java.util.HashMap<>();
        for (DelayRecord r : rows) latest.putIfAbsent(new Key(r.tripId, r.stopId, r.serviceDate), r);
        return connections.stream().map(c -> c.withDelay(
                delay(latest.get(new Key(c.tripId(), homeStopId, c.serviceDate())))
                        .or(() -> delay(latest.get(new Key(c.tripId(), schoolStopId, c.serviceDate())))).orElse(null)))
                .toList();
    }

    /** A row with no real-time data (null delay) counts as no delay known, so the next stop is tried. */
    private static java.util.Optional<Integer> delay(DelayRecord r) {
        return r == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(r.reportedDelay);
    }
}
