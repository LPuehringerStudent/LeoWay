package at.leoway.api.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.FeedStatus.State;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FeedPollerTest {
    private static final Duration INTERVAL = Duration.ofSeconds(10);

    private final MutableClock clock = new MutableClock();
    private final MemoryStore store = new MemoryStore();
    private final RecordingSink sink = new RecordingSink();

    private FeedPoller poller(FeedAdapter... adapters) {
        return new FeedPoller(List.of(adapters), sink, store, clock, Runnable::run);
    }

    @Test
    void throwingAdapterDoesNotStopTheOther() {
        FakeAdapter broken = new FakeAdapter("broken").failing();
        FakeAdapter healthy = new FakeAdapter("healthy");
        FeedPoller poller = poller(broken, healthy);

        poller.tick();
        clock.advance(INTERVAL);
        poller.tick();

        assertEquals(2, healthy.polls.get());
        assertEquals(2, sink.batches.get("healthy").get());
        assertEquals(State.OK, store.saved.get("healthy").state());
    }

    @Test
    void observationsReachTheSinkWithSourceAndCollectionTime() {
        poller(new FakeAdapter("linz")).tick();

        assertEquals(clock.instant(), sink.lastCollectedAt);
        assertEquals("linz", sink.lastSource);
    }

    @Test
    void backOffGrowsUpToTenMinutes() {
        FakeAdapter broken = new FakeAdapter("broken").failing();
        FeedPoller poller = poller(broken);
        poller.tick();

        int polls = 1;
        for (Duration wait : List.of(seconds(30), seconds(60), seconds(120), seconds(240), seconds(480), seconds(600), seconds(600))) {
            clock.advance(wait.minusSeconds(1));
            poller.tick();
            assertEquals(polls, broken.polls.get(), "too early, before " + wait);

            clock.advance(Duration.ofSeconds(1));
            poller.tick();
            polls++;
            assertEquals(polls, broken.polls.get(), "after " + wait);
        }
    }

    @Test
    void backOffNeverPollsFasterThanTheFeedInterval() {
        FakeAdapter slow = new FakeAdapter("slow", Duration.ofMinutes(15)).failing();
        FeedPoller poller = poller(slow);
        poller.tick();

        clock.advance(Duration.ofMinutes(14));
        poller.tick();
        assertEquals(1, slow.polls.get());
    }

    @Test
    void successResetsTheBackOff() {
        FakeAdapter flaky = new FakeAdapter("flaky").failing();
        FeedPoller poller = poller(flaky);
        poller.tick();
        clock.advance(seconds(30));
        poller.tick();

        flaky.working();
        clock.advance(seconds(60));
        poller.tick();
        assertEquals(3, flaky.polls.get());

        flaky.failing();
        clock.advance(INTERVAL);
        poller.tick();
        clock.advance(seconds(30));
        poller.tick();
        assertEquals(5, flaky.polls.get());
    }

    @Test
    void feedThatNeverSucceededIsDown() {
        poller(new FakeAdapter("broken").failing()).tick();

        FeedHealth health = store.saved.get("broken");
        assertEquals(State.DOWN, health.state());
        assertNull(health.lastSuccessAt());
        assertEquals(clock.instant(), health.lastErrorAt());
        assertTrue(health.lastError().contains("feed unavailable"), health.lastError());
    }

    @Test
    void feedStaysOkUntilItsLastSuccessIsOlderThanFiveMinutes() {
        FakeAdapter feed = new FakeAdapter("linz");
        FeedPoller poller = poller(feed);
        poller.tick();
        feed.failing();

        clock.advance(Duration.ofMinutes(5));
        poller.tick();
        assertEquals(State.OK, store.saved.get("linz").state());
        assertNotNull(store.saved.get("linz").lastError());

        clock.advance(Duration.ofSeconds(1));
        poller.tick();
        assertEquals(State.STALE, store.saved.get("linz").state());
    }

    @Test
    void recoveryReturnsToOk() {
        FakeAdapter feed = new FakeAdapter("linz");
        FeedPoller poller = poller(feed);
        poller.tick();
        feed.failing();
        clock.advance(Duration.ofMinutes(6));
        poller.tick();
        assertEquals(State.STALE, store.saved.get("linz").state());

        feed.working();
        clock.advance(seconds(30));
        poller.tick();

        assertEquals(State.OK, store.saved.get("linz").state());
        assertEquals(clock.instant(), store.saved.get("linz").lastSuccessAt());
    }

    @Test
    void lastSuccessSurvivesARestart() {
        Instant lastSuccess = clock.instant().minus(Duration.ofMinutes(6));
        store.saved.put("linz", new FeedHealth("linz", State.OK, lastSuccess, null, null));

        poller(new FakeAdapter("linz").failing()).tick();

        assertEquals(State.STALE, store.saved.get("linz").state());
        assertEquals(lastSuccess, store.saved.get("linz").lastSuccessAt());
    }

    @Test
    void sinkFailureCountsAsAFailedPoll() {
        sink.failing = true;

        poller(new FakeAdapter("linz")).tick();

        assertEquals(State.DOWN, store.saved.get("linz").state());
    }

    @Test
    void failingStatusStoreDoesNotStopPolling() {
        store.failing = true;
        FakeAdapter feed = new FakeAdapter("linz");
        FeedPoller poller = poller(feed);

        poller.tick();
        clock.advance(INTERVAL);
        poller.tick();

        assertEquals(2, feed.polls.get());
    }

    @Test
    void hangingAdapterDoesNotHoldUpOthersAndTurnsStale() throws Exception {
        FakeAdapter hanging = new FakeAdapter("hanging");
        FakeAdapter healthy = new FakeAdapter("healthy");
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            FeedPoller poller = new FeedPoller(List.of(hanging, healthy), sink, store, clock, executor);
            // Keep ticking like the real scheduler: a feed can't start again until its last poll has finished.
            await(poller, () -> store.saved.containsKey("hanging") && store.saved.containsKey("healthy"));

            hanging.hang();
            clock.advance(INTERVAL);
            await(poller, () -> hanging.polls.get() == 2 && healthy.polls.get() == 2);

            // Still hanging: it isn't started again, the other feed carries on, and its status goes stale.
            clock.advance(Duration.ofMinutes(6));
            await(poller, () -> healthy.polls.get() == 3 && store.saved.get("hanging").state() == State.STALE);
            assertEquals(2, hanging.polls.get());
        } finally {
            hanging.release();
            executor.shutdownNow();
        }
    }

    private static Duration seconds(int seconds) {
        return Duration.ofSeconds(seconds);
    }

    private static void await(FeedPoller poller, java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 500 && !condition.getAsBoolean(); i++) {
            poller.tick();
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition not reached in time");
    }

    private static final class FakeAdapter implements FeedAdapter {
        final AtomicInteger polls = new AtomicInteger();
        private final String id;
        private final Duration interval;
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile boolean failing;
        private volatile boolean hanging;

        FakeAdapter(String id) {
            this(id, INTERVAL);
        }

        FakeAdapter(String id, Duration interval) {
            this.id = id;
            this.interval = interval;
        }

        FakeAdapter failing() {
            failing = true;
            return this;
        }

        void working() {
            failing = false;
        }

        void hang() {
            hanging = true;
        }

        void release() {
            released.countDown();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Duration pollInterval() {
            return interval;
        }

        @Override
        public List<DepartureObservation> fetch() throws Exception {
            polls.incrementAndGet();
            if (hanging) {
                released.await();
            }
            if (failing) {
                throw new IllegalStateException("feed unavailable");
            }
            return List.of();
        }
    }

    private static final class RecordingSink implements ObservationSink {
        final Map<String, AtomicInteger> batches = new ConcurrentHashMap<>();
        volatile boolean failing;
        volatile String lastSource;
        volatile Instant lastCollectedAt;

        @Override
        public void store(String source, Instant collectedAt, List<DepartureObservation> observations) {
            if (failing) {
                throw new IllegalStateException("database unavailable");
            }
            batches.computeIfAbsent(source, key -> new AtomicInteger()).incrementAndGet();
            lastSource = source;
            lastCollectedAt = collectedAt;
        }
    }

    private static final class MemoryStore extends FeedStatusStore {
        final Map<String, FeedHealth> saved = new ConcurrentHashMap<>();
        volatile boolean failing;

        @Override
        public Optional<FeedHealth> find(String feedId) {
            return Optional.ofNullable(saved.get(feedId));
        }

        @Override
        public List<FeedHealth> findAll() {
            return new ArrayList<>(saved.values());
        }

        @Override
        public void save(FeedHealth health) {
            if (failing) {
                throw new IllegalStateException("database unavailable");
            }
            saved.put(health.feedId(), health);
        }
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-04T06:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
