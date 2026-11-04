package at.leoway.api.feed;

import java.time.Duration;
import java.util.List;

/**
 * One transport feed. To add a feed, add one CDI bean that implements this interface (M01).
 */
public interface FeedAdapter {
    /** Stable name of the feed. Used as the record source and the feed status id. */
    String id();

    /** Time between polls while the feed is healthy. */
    Duration pollInterval();

    /**
     * Fetches the current departures. Any exception counts as a failed poll.
     *
     * <p>Implementations must use network timeouts: the next poll of this feed only starts after this one returns.
     */
    List<DepartureObservation> fetch() throws Exception;
}
