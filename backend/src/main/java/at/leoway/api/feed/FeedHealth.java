package at.leoway.api.feed;

import at.leoway.api.domain.FeedStatus;
import java.time.Instant;

/** The stored status of one feed. */
public record FeedHealth(
        String feedId,
        FeedStatus.State state,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        String lastError) {
}
