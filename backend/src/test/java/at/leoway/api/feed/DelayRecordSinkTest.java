package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.leoway.api.domain.DelayRecord;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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

    static DepartureObservation obs(Integer delay) {
        return new DepartureObservation("19", "trip-sink-test", "60501780", "1", LocalDate.now(LinzAgAdapter.LINZ),
                T0.plusSeconds(1800), delay, "MONITORED");
    }

    @AfterEach
    void clean() {
        // delay_record forbids UPDATE, not DELETE.
        QuarkusTransaction.requiringNew().run(() -> DelayRecord.delete("tripId", "trip-sink-test"));
    }

    long rows() {
        return QuarkusTransaction.requiringNew().call(() -> DelayRecord.count("tripId", "trip-sink-test"));
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
}
