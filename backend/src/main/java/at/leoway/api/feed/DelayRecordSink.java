package at.leoway.api.feed;

import at.leoway.api.domain.DelayRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores observations as delay records (#6). A row is added only when a departure is new or its delay or trip
 * status changed since the last stored row, so the table holds every change, not every poll (#3, #18).
 * Rows are never updated (S04).
 *
 * <p>Linz AG sometimes leaves out {@code AVMSTripID} for single sightings of a departure, and the adapter then uses a
 * fallback id with {@code #}. So that one departure keeps one trip id (#69), a fallback id is replaced by the id already
 * stored for the same departure (source, line, stop, bay, planned time), and the first id stored wins over a later
 * real id. Fallback sightings more than {@link #FALLBACK_HORIZON} away are skipped: the delay is always 0 that far out
 * (#3), and the real id usually arrives before the departure gets close.
 */
@ApplicationScoped
public class DelayRecordSink implements ObservationSink {
    record Key(String source, String tripId, String stopId, String bay, java.time.LocalDate serviceDate) {}

    record Last(Integer delay, String tripStatus) {}

    /** One departure at one stop, independent of the trip id the feed sent. */
    record Departure(String source, String line, String stopId, String bay, Instant plannedAt) {}

    static final Duration FALLBACK_HORIZON = Duration.ofMinutes(60);

    /** Last stored value per departure. Filled from the database on a miss, so restarts don't duplicate rows. */
    private final Map<Key, Last> last = new ConcurrentHashMap<>();

    /** Trip id stored first per departure. Filled from the database on a miss, like {@link #last}. */
    private final Map<Departure, String> tripIds = new ConcurrentHashMap<>();

    @Override
    public void store(String source, Instant collectedAt, List<DepartureObservation> observations) {
        Map<Key, Last> stored = new HashMap<>();
        Map<Departure, String> newIds = new HashMap<>();
        QuarkusTransaction.requiringNew().run(() -> {
            for (DepartureObservation o : observations) {
                Departure departure = new Departure(source, o.line(), o.stopId(), o.bay(), o.plannedAt());
                String known = newIds.containsKey(departure) ? newIds.get(departure) : knownTripId(departure);
                String tripId = tripId(o, known, collectedAt);
                if (tripId == null) continue;
                Key key = new Key(source, tripId, o.stopId(), o.bay(), o.serviceDate());
                Last now = new Last(o.reportedDelay(), o.tripStatus());
                Last before = stored.containsKey(key) ? stored.get(key) : last.computeIfAbsent(key, this::latestStored);
                if (before != null && before.equals(now)) continue;
                DelayRecord r = new DelayRecord();
                r.source = source;
                r.line = o.line();
                r.tripId = tripId;
                r.stopId = o.stopId();
                r.bay = o.bay();
                r.serviceDate = o.serviceDate();
                r.plannedAt = o.plannedAt();
                r.reportedDelay = o.reportedDelay();
                r.tripStatus = o.tripStatus();
                r.collectedAt = collectedAt;
                r.persist();
                stored.put(key, now);
                if (known == null) newIds.put(departure, tripId);
            }
        });
        // Only after the commit: a failed batch must not count as stored.
        last.putAll(stored);
        tripIds.putAll(newIds);
        tripIds.keySet().removeIf(d -> d.plannedAt().isBefore(collectedAt.minus(Duration.ofDays(2))));
        // Departures from earlier days are never seen again.
        last.keySet().removeIf(k -> k.serviceDate().isBefore(java.time.LocalDate.now(LinzAgAdapter.LINZ).minusDays(1)));
    }

    /**
     * The trip id to store, or null to skip the sighting. Real ids are kept unless the departure was first stored
     * under a fallback id; fallback ids are replaced by any id already stored.
     */
    static String tripId(DepartureObservation o, String known, Instant collectedAt) {
        boolean fallback = LinzAgAdapter.isFallbackTripId(o.tripId());
        if (known == null) {
            boolean far = o.plannedAt().isAfter(collectedAt.plus(FALLBACK_HORIZON));
            return fallback && far ? null : o.tripId();
        }
        return fallback || LinzAgAdapter.isFallbackTripId(known) ? known : o.tripId();
    }

    private String knownTripId(Departure d) {
        String cached = tripIds.get(d);
        if (cached != null) return cached;
        DelayRecord r = DelayRecord.<DelayRecord>find(
                "source = ?1 and line = ?2 and stopId = ?3 and plannedAt = ?4 and "
                        + (d.bay() == null ? "bay is null" : "bay = ?5") + " order by collectedAt",
                d.bay() == null ? new Object[] {d.source(), d.line(), d.stopId(), d.plannedAt()}
                        : new Object[] {d.source(), d.line(), d.stopId(), d.plannedAt(), d.bay()})
                .firstResult();
        if (r != null) tripIds.put(d, r.tripId);
        return r == null ? null : r.tripId;
    }

    private Last latestStored(Key k) {
        DelayRecord r = DelayRecord.<DelayRecord>find(
                "source = ?1 and tripId = ?2 and stopId = ?3 and serviceDate = ?4 and "
                        + (k.bay() == null ? "bay is null" : "bay = ?5") + " order by collectedAt desc",
                k.bay() == null ? new Object[] {k.source(), k.tripId(), k.stopId(), k.serviceDate()}
                        : new Object[] {k.source(), k.tripId(), k.stopId(), k.serviceDate(), k.bay()})
                .firstResult();
        return r == null ? null : new Last(r.reportedDelay, r.tripStatus);
    }

}
