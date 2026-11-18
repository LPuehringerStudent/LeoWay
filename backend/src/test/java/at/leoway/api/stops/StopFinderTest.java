package at.leoway.api.stops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import at.leoway.api.stops.StopFinder.Stop;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parsing, cache expiry and the request gap, without Quarkus. */
class StopFinderTest {
    FakeLinzAg fake;
    StopFinder finder;
    Instant now = Instant.parse("2026-11-18T07:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeLinzAg(0);
        finder = new StopFinder();
        finder.json = new ObjectMapper();
        finder.baseUrl = fake.baseUrl();
        finder.cacheTtl = Duration.ofDays(1);
        finder.minGap = Duration.ofSeconds(2);
        finder.useClock(new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        });
    }

    @AfterEach
    void tearDown() {
        fake.close();
    }

    @Test
    void parsesOneMatchAsObjectAndSeveralAsArray() throws Exception {
        ObjectMapper json = new ObjectMapper();
        assertEquals(List.of(new Stop("60500120", "Leonding, Meixnerkreuzung")),
                StopFinder.parse(json.readTree(getClass().getResourceAsStream("/linz-ag/stopfinder-one.json"))));
        assertEquals(List.of("60501810", "60502210", "60508077"),
                StopFinder.parse(json.readTree(getClass().getResourceAsStream("/linz-ag/stopfinder-many.json")))
                        .stream().map(Stop::id).toList());
        assertEquals(List.of(),
                StopFinder.parse(json.readTree(getClass().getResourceAsStream("/linz-ag/stopfinder-none.json"))));
    }

    @Test
    void spacesUncachedRequestsButServesCachedOnes() throws Exception {
        finder.search("auwiesen");
        StopFinder.Busy busy = assertThrows(StopFinder.Busy.class, () -> finder.search("meixnerkr"));
        assertEquals(Duration.ofSeconds(2), busy.retryAfter);
        assertEquals(3, finder.search("Auwiesen").size(), "cached, so not limited");

        now = now.plusSeconds(2);
        assertEquals(1, finder.search("meixnerkr").size());
        assertEquals(2, fake.queries.size());
    }

    @Test
    void refetchesAfterTheCacheExpires() throws Exception {
        finder.search("auwiesen");
        now = now.plus(Duration.ofDays(1)).plusSeconds(1);
        finder.search("auwiesen");
        assertEquals(2, fake.queries.size());
    }
}
