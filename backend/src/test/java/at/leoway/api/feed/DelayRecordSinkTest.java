package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.leoway.api.domain.DelayRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** #6: one row per change, not per poll; restarts don't duplicate. */
@QuarkusTest
class DelayRecordSinkTest {
    @Inject
    DelayRecordSink sink;

    static final Instant T0 = Instant.parse("2026-11-05T06:00:00Z");

    static final String FALLBACK = "esg:02019:E:H:f26#sink-test";

    static DepartureObservation obs(Integer delay) {
        return obs("trip-sink-test", delay);
    }

    static DepartureObservation obs(String tripId, Integer delay) {
        return obs(tripId, delay, T0.plusSeconds(1800));
    }

    static DepartureObservation obs(String tripId, Integer delay, Instant plannedAt) {
        return new DepartureObservation("19", tripId, "60501780", "1", LocalDate.now(LinzAgAdapter.LINZ),
                plannedAt, delay, "MONITORED");
    }

    @AfterEach
    void clean() {
        // delay_record forbids UPDATE, not DELETE.
        QuarkusTransaction.requiringNew().run(() -> DelayRecord.delete("tripId in ?1",
                List.of("trip-sink-test", "trip-sink-test-2", FALLBACK)));
    }

    long rows() {
        return rows("trip-sink-test");
    }

    long rows(String tripId) {
        return QuarkusTransaction.requiringNew().call(() -> DelayRecord.count("tripId", tripId));
    }

    @Test
    void storesOnlyChanges() {
        sink.store("test", T0, List.of(obs(2)));
        sink.store("test", T0.plusSeconds(30), List.of(obs(2)));
        sink.store("test", T0.plusSeconds(60), List.of(obs(4)));
        sink.store("test", T0.plusSeconds(90), List.of(obs(4)));
        sink.store("test", T0.plusSeconds(120), List.of(obs(null)));
        assertEquals(3, rows());
    }

    @Test
    void unchangedValueAfterRestartIsNotStoredAgain() {
        sink.store("test", T0, List.of(obs(2)));
        DelayRecordSink fresh = new DelayRecordSink();
        fresh.store("test", T0.plusSeconds(30), List.of(obs(2)));
        assertEquals(1, rows());
    }

    @Test
    void failedBatchIsStoredByTheNextPoll() {
        DelayRecordSink fresh = new DelayRecordSink();
        DepartureObservation invalid = new DepartureObservation(null, "trip-sink-test", "60501780", "2",
                LocalDate.now(LinzAgAdapter.LINZ), T0.plusSeconds(1800), 1, "MONITORED");

        assertThrows(RuntimeException.class, () -> fresh.store("test", T0, List.of(obs(2), invalid)));
        assertEquals(0, rows());

        fresh.store("test", T0.plusSeconds(30), List.of(obs(2)));
        assertEquals(1, rows());
    }

    @Test
    void repeatedDepartureInOneBatchIsStoredOnce() {
        new DelayRecordSink().store("test", T0, List.of(obs(2), obs(2)));
        assertEquals(1, rows());
    }

    // #69: one departure keeps one trip id when Linz AG leaves out AVMSTripID for some sightings.

    @Test
    void sightingWithoutAvmsTripIdKeepsTheStoredId() {
        DelayRecordSink fresh = new DelayRecordSink();
        fresh.store("test", T0, List.of(obs(1)));
        fresh.store("test", T0.plusSeconds(30), List.of(obs(FALLBACK, 2)));
        fresh.store("test", T0.plusSeconds(60), List.of(obs(3)));
        assertEquals(3, rows());
        assertEquals(0, rows(FALLBACK));
    }

    @Test
    void storedIdIsFoundAfterARestart() {
        new DelayRecordSink().store("test", T0, List.of(obs(1)));
        new DelayRecordSink().store("test", T0.plusSeconds(30), List.of(obs(FALLBACK, 2)));
        assertEquals(2, rows());
        assertEquals(0, rows(FALLBACK));
    }

    @Test
    void departureFirstStoredWithoutAvmsTripIdKeepsTheFallbackId() {
        DelayRecordSink fresh = new DelayRecordSink();
        fresh.store("test", T0, List.of(obs(FALLBACK, 1)));
        fresh.store("test", T0.plusSeconds(30), List.of(obs(2)));
        assertEquals(2, rows(FALLBACK));
        assertEquals(0, rows());
    }

    @Test
    void fallbackSightingMoreThanAnHourAwayIsSkipped() {
        DelayRecordSink fresh = new DelayRecordSink();
        Instant planned = T0.plus(Duration.ofMinutes(90));
        fresh.store("test", T0, List.of(obs(FALLBACK, 0, planned)));
        assertEquals(0, rows(FALLBACK));

        fresh.store("test", T0.plusSeconds(30), List.of(obs("trip-sink-test", 0, planned)));
        assertEquals(1, rows());
    }

    @Test
    void twoRealIdsForOneDepartureStaySeparate() {
        // Seen on trams 3 and 4: ids differing only in the suffix may be two vehicles, so they are not merged.
        DelayRecordSink fresh = new DelayRecordSink();
        fresh.store("test", T0, List.of(obs(1), obs("trip-sink-test-2", 1)));
        assertEquals(1, rows());
        assertEquals(1, rows("trip-sink-test-2"));
    }
}
