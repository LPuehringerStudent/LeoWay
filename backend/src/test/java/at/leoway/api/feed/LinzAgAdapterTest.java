package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parses a recorded (synthetic) Linz AG departure monitor response. */
class LinzAgAdapterTest {
    @Test
    void parsesDepartures() throws Exception {
        var body = new ObjectMapper().readTree(getClass().getResourceAsStream("/linz-ag/departures.json"));
        List<DepartureObservation> d = LinzAgAdapter.parse("60500120", body);
        assertEquals(3, d.size());

        DepartureObservation bus19 = d.getFirst();
        assertEquals("19", bus19.line());
        assertEquals("T-19-0735", bus19.tripId());
        assertEquals("1", bus19.bay());
        assertEquals(LocalDate.of(2026, 11, 5), bus19.serviceDate());
        // 07:35 in Linz (CET) is 06:35 UTC.
        assertEquals(Instant.parse("2026-11-05T06:35:00Z"), bus19.plannedAt());
        assertEquals(3, bus19.reportedDelay());
        assertEquals("MONITORED", bus19.tripStatus());

        assertEquals("lil:03003:E:R:f26#77", d.get(1).tripId(), "without AVMSTripID: line id + trip key");
        assertEquals(-1, d.get(1).reportedDelay());

        assertNull(d.get(2).reportedDelay(), "-9999 means no real-time data");
        assertNull(d.get(2).bay());
        assertNull(d.get(2).tripStatus());
    }
}
