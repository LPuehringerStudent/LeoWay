package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.FeedStatus.State;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FeedStatusStoreTest {
    private static final Instant TIME = Instant.parse("2026-11-04T06:00:00Z");

    @Inject
    FeedStatusStore store;

    @Test
    @TestTransaction
    void savingTwiceUpdatesTheSameRow() {
        store.save(new FeedHealth("store-test", State.OK, TIME, null, null));
        FeedHealth stale = new FeedHealth("store-test", State.STALE, TIME, TIME.plusSeconds(60), "IOException: timeout");
        store.save(stale);

        assertEquals(stale, store.find("store-test").orElseThrow());
        assertEquals(1, store.findAll().stream().filter(health -> health.feedId().equals("store-test")).count());
    }

    @Test
    @TestTransaction
    void unknownFeedHasNoStatus() {
        assertTrue(store.find("no-such-feed").isEmpty());
    }
}
