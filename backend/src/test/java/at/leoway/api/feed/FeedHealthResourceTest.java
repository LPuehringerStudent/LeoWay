package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.leoway.api.domain.FeedStatus.State;
import at.leoway.api.feed.FeedHealthResource.FeedState;
import at.leoway.api.feed.FeedHealthResource.Report;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FeedHealthResourceTest {
    private static final Instant NOW = Instant.parse("2026-11-05T06:00:00Z");

    private final Map<String, FeedHealth> rows = new HashMap<>();

    private Response check(String... feedIds) {
        FeedHealthResource resource = new FeedHealthResource();
        resource.adapters = List.of(feedIds).stream().<FeedAdapter>map(FeedHealthResourceTest::adapter).toList();
        resource.store = new FeedStatusStore() {
            @Override
            public Optional<FeedHealth> find(String feedId) {
                return Optional.ofNullable(rows.get(feedId));
            }
        };
        resource.clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return resource.check();
    }

    private void row(String feedId, State state, Duration sinceLastSuccess) {
        Instant lastSuccess = sinceLastSuccess == null ? null : NOW.minus(sinceLastSuccess);
        rows.put(feedId, new FeedHealth(feedId, state, lastSuccess, null, null));
    }

    private static List<FeedState> feeds(Response response) {
        return ((Report) response.getEntity()).feeds();
    }

    @Test
    void okWhenEveryFeedIsOk() {
        row("linz", State.OK, Duration.ofSeconds(30));
        row("mvo", State.OK, Duration.ofMinutes(1));

        Response response = check("linz", "mvo");

        assertEquals(200, response.getStatus());
        assertEquals("UP", ((Report) response.getEntity()).status());
    }

    @Test
    void okWithoutAnyFeeds() {
        assertEquals(200, check().getStatus());
    }

    @Test
    void staleFeedFailsTheCheckAndTheOtherStaysVisible() {
        row("linz", State.STALE, Duration.ofMinutes(7));
        row("mvo", State.OK, Duration.ofSeconds(30));

        Response response = check("linz", "mvo");

        assertEquals(503, response.getStatus());
        assertEquals("DOWN", ((Report) response.getEntity()).status());
        assertEquals(List.of(new FeedState("linz", "STALE"), new FeedState("mvo", "OK")), feeds(response));
    }

    @Test
    void downFeedFailsTheCheck() {
        row("linz", State.DOWN, null);

        assertEquals(503, check("linz").getStatus());
    }

    @Test
    void feedWithoutAStatusFailsTheCheck() {
        Response response = check("linz");

        assertEquals(503, response.getStatus());
        assertEquals(List.of(new FeedState("linz", FeedHealthResource.NO_STATUS)), feeds(response));
    }

    @Test
    void okRowWithAnOldSuccessCountsAsStale() {
        row("linz", State.OK, Duration.ofMinutes(5).plusSeconds(1));

        Response response = check("linz");

        assertEquals(503, response.getStatus());
        assertEquals(List.of(new FeedState("linz", "STALE")), feeds(response));
    }

    @Test
    void okRowAtExactlyFiveMinutesIsStillOk() {
        row("linz", State.OK, Duration.ofMinutes(5));

        assertEquals(200, check("linz").getStatus());
    }

    @Test
    void feedThatIsNoLongerConfiguredIsIgnored() {
        row("removed", State.STALE, Duration.ofDays(30));
        row("linz", State.OK, Duration.ofSeconds(30));

        assertEquals(200, check("linz").getStatus());
    }

    private static FeedAdapter adapter(String id) {
        return new FeedAdapter() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public Duration pollInterval() {
                return Duration.ofSeconds(30);
            }

            @Override
            public List<DepartureObservation> fetch() {
                return List.of();
            }
        };
    }
}
