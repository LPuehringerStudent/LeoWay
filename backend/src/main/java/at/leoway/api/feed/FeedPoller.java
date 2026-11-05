package at.leoway.api.feed;

import at.leoway.api.domain.FeedStatus.State;
import io.quarkus.arc.All;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.logging.Logger;

/**
 * Polls every {@link FeedAdapter} on its own schedule. Each poll runs on its own thread, so a failing or
 * hanging feed cannot hold up the others (S06).
 *
 * <p>States: OK while the last success is at most five minutes old (E03), then STALE. DOWN means the feed
 * is failing and has never succeeded.
 */
@Singleton
public class FeedPoller {
    static final Duration STALE_AFTER = Duration.ofMinutes(5);
    private static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);
    private static final int MAX_ERROR_LENGTH = 500;
    private static final Logger LOG = Logger.getLogger(FeedPoller.class);

    private final List<Feed> feeds;
    private final ObservationSink sink;
    private final FeedStatusStore store;
    private final Clock clock;
    private final Executor executor;

    @Inject
    public FeedPoller(@All List<FeedAdapter> adapters, ObservationSink sink, FeedStatusStore store) {
        this(adapters, sink, store, Clock.systemUTC(), Executors.newVirtualThreadPerTaskExecutor());
    }

    FeedPoller(List<FeedAdapter> adapters, ObservationSink sink, FeedStatusStore store, Clock clock, Executor executor) {
        this.feeds = adapters.stream().map(Feed::new).toList();
        this.sink = sink;
        this.store = store;
        this.clock = clock;
        this.executor = executor;
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        Instant now = clock.instant();
        for (Feed feed : feeds) {
            if (feed.markStaleIfOverdue(now)) {
                executor.execute(() -> saveStatus(feed));
            }
            if (feed.claimIfDue(now)) {
                executor.execute(() -> poll(feed));
            }
        }
    }

    @PreDestroy
    void stop() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    private void poll(Feed feed) {
        String id = feed.adapter.id();
        try {
            restore(feed);
            List<DepartureObservation> observations = feed.adapter.fetch();
            Instant collectedAt = clock.instant();
            sink.store(id, collectedAt, observations);
            feed.succeeded(collectedAt);
        } catch (Exception e) {
            LOG.warnf(e, "Feed %s failed", id);
            feed.failed(clock.instant(), e);
        }
        saveStatus(feed);
        feed.release();
    }

    /** After a restart the last success comes from the database, so a working feed isn't reported as DOWN. */
    private void restore(Feed feed) {
        if (!feed.restored) {
            store.find(feed.adapter.id()).ifPresent(feed::restore);
            feed.restored = true;
        }
    }

    private void saveStatus(Feed feed) {
        // One writer per feed at a time, so an older snapshot can't overwrite a newer one.
        synchronized (feed.saveLock) {
            try {
                store.save(feed.health());
            } catch (Exception e) {
                LOG.warnf(e, "Could not save the status of feed %s", feed.adapter.id());
            }
        }
    }

    private static final class Feed {
        final FeedAdapter adapter;
        final Object saveLock = new Object();
        volatile boolean restored;

        private boolean running;
        private Instant nextPollAt = Instant.MIN;
        private int failures;
        private State state;
        private Instant lastSuccessAt;
        private Instant lastErrorAt;
        private String lastError;

        Feed(FeedAdapter adapter) {
            this.adapter = adapter;
        }

        synchronized boolean claimIfDue(Instant now) {
            if (running || now.isBefore(nextPollAt)) {
                return false;
            }
            running = true;
            return true;
        }

        synchronized void release() {
            running = false;
        }

        synchronized void restore(FeedHealth saved) {
            state = saved.state();
            lastSuccessAt = saved.lastSuccessAt();
            lastErrorAt = saved.lastErrorAt();
            lastError = saved.lastError();
        }

        synchronized void succeeded(Instant now) {
            failures = 0;
            state = State.OK;
            lastSuccessAt = now;
            nextPollAt = now.plus(adapter.pollInterval());
        }

        synchronized void failed(Instant now, Exception error) {
            failures++;
            lastErrorAt = now;
            lastError = describe(error);
            if (lastSuccessAt == null) {
                state = State.DOWN;
            } else {
                state = isOverdue(now) ? State.STALE : State.OK;
            }
            nextPollAt = now.plus(longer(adapter.pollInterval(), backoff(failures)));
        }

        synchronized boolean markStaleIfOverdue(Instant now) {
            if (state == State.OK && isOverdue(now)) {
                state = State.STALE;
                return true;
            }
            return false;
        }

        synchronized FeedHealth health() {
            return new FeedHealth(adapter.id(), state, lastSuccessAt, lastErrorAt, lastError);
        }

        private boolean isOverdue(Instant now) {
            return lastSuccessAt != null && Duration.between(lastSuccessAt, now).compareTo(STALE_AFTER) > 0;
        }

        private static Duration backoff(int failures) {
            // 30 s, 1, 2, 4, 8, then 10 min. The shift limit keeps the value from overflowing.
            Duration doubled = FIRST_BACKOFF.multipliedBy(1L << Math.min(failures - 1, 5));
            return doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
        }

        private static Duration longer(Duration a, Duration b) {
            return a.compareTo(b) > 0 ? a : b;
        }

        private static String describe(Exception error) {
            String text = error.getClass().getSimpleName() + ": " + error.getMessage();
            return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
        }
    }
}
