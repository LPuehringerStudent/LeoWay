package at.leoway.api.feed;

import at.leoway.api.domain.DelayRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
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
 */
@ApplicationScoped
public class DelayRecordSink implements ObservationSink {
    record Key(String source, String tripId, String stopId, String bay, java.time.LocalDate serviceDate) {}

    record Last(Integer delay, String tripStatus) {}

    /** Last stored value per departure. Filled from the database on a miss, so restarts don't duplicate rows. */
    private final Map<Key, Last> last = new ConcurrentHashMap<>();

    @Override
    public void store(String source, Instant collectedAt, List<DepartureObservation> observations) {
        Map<Key, Last> stored = new HashMap<>();
        QuarkusTransaction.requiringNew().run(() -> {
            for (DepartureObservation o : observations) {
                Key key = new Key(source, o.tripId(), o.stopId(), o.bay(), o.serviceDate());
                Last now = new Last(o.reportedDelay(), o.tripStatus());
                Last before = stored.containsKey(key) ? stored.get(key) : last.computeIfAbsent(key, this::latestStored);
                if (before != null && before.equals(now)) continue;
                DelayRecord r = new DelayRecord();
                r.source = source;
                r.line = o.line();
                r.tripId = o.tripId();
                r.stopId = o.stopId();
                r.bay = o.bay();
                r.serviceDate = o.serviceDate();
                r.plannedAt = o.plannedAt();
                r.reportedDelay = o.reportedDelay();
                r.tripStatus = o.tripStatus();
                r.collectedAt = collectedAt;
                r.persist();
                stored.put(key, now);
            }
        });
        // Only after the commit: a failed batch must not count as stored.
        last.putAll(stored);
        // Departures from earlier days are never seen again.
        last.keySet().removeIf(k -> k.serviceDate().isBefore(java.time.LocalDate.now(LinzAgAdapter.LINZ).minusDays(1)));
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
