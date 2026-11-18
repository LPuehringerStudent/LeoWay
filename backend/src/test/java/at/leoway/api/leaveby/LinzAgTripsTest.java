package at.leoway.api.leaveby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reading the Linz AG trip request (#58). The fixture is a real answer (Hauptbahnhof to Limesstraße, arrive by 07:50
 * on 19.11.2026), trimmed, with real-time data added to the 07:39 trip and an AVMSTripID to the 07:16 one.
 */
class LinzAgTripsTest {
    static final ObjectMapper JSON = new ObjectMapper();

    static JsonNode fixture() throws Exception {
        try (InputStream in = LinzAgTripsTest.class.getResourceAsStream("/linz-ag/trip-arrive-by.json")) {
            return JSON.readTree(in);
        }
    }

    static String local(java.time.Instant t) {
        return LeaveBy.local(t).toString();
    }

    @Test
    void readsEveryTripWithItsLinesAndTimes() throws Exception {
        List<Connection> cs = LinzAgTrips.parse(fixture());
        assertEquals(List.of("3 > 19", "4 > 191", "3 > 11", "3 > 19"), cs.stream().map(Connection::line).toList(),
                "walks are not lines");
        Connection first = cs.getFirst();
        assertEquals("07:09", local(first.plannedDeparture()));
        assertEquals("07:21", local(first.plannedArrival()));
        assertEquals(LocalDate.of(2026, 11, 19), first.serviceDate());
        assertEquals("07:30", local(cs.get(1).plannedArrival()), "arrival after the walk at the end");
    }

    @Test
    void tripIdsMatchTheRecorder() throws Exception {
        List<Connection> cs = LinzAgTrips.parse(fixture());
        assertEquals("esg:01003:E:H:f26#168", cs.getFirst().tripId(), "stateless#tripCode like LinzAgAdapter");
        assertEquals("4711", cs.get(1).tripId(), "AVMSTripID when there is one");
    }

    @Test
    void realTimeOnlyCountsWhenTheRideHasIt() throws Exception {
        List<Connection> cs = LinzAgTrips.parse(fixture());
        Connection planned = cs.get(2);
        assertFalse(planned.predicted(), "rtTime equal to the timetable and realtime 0 is not live data");
        assertNull(planned.delayMinutes());

        Connection live = cs.get(3);
        assertTrue(live.predicted());
        assertEquals(3, live.delayMinutes(), "first ride leaves 3 min late");
        assertEquals(2, live.arrivalDelayMinutes(), "last ride arrives 2 min late");
        assertEquals("07:42", local(live.departure()));
        assertEquals("07:53", local(live.arrival()));
    }

    @Test
    void leavesOutCancelledTrips() throws Exception {
        JsonNode body = fixture();
        ((ObjectNode) body.path("trips").get(0).path("legs").get(1)).put("realtimeStatus", "TRIP_CANCELLED");
        assertEquals(3, LinzAgTrips.parse(body).size());
    }

    @Test
    void handlesASingleTripAsAnObjectAndNoTrips() throws Exception {
        ObjectNode one = JSON.createObjectNode();
        one.set("trips", fixture().path("trips").get(2));
        assertEquals(List.of("3 > 11"), LinzAgTrips.parse(one).stream().map(Connection::line).toList());
        assertEquals(List.of(), LinzAgTrips.parse(JSON.readTree("{\"trips\": null}")));
    }
}
