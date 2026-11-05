package at.leoway.api.feed;

import at.leoway.api.domain.FeedStatus.State;
import io.quarkus.arc.All;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Lets Uptime Kuma alert the team when a feed stops working (F09, A09). It needs no login, so it shows only feed
 * names and states. Error details stay on the admin endpoint.
 *
 * <p>This is separate from {@code /api/health/ready}: a stale feed must not make the whole API look down.
 */
@Path("/health/feeds")
@Tag(name = "Health", description = "Health checks for monitoring.")
public class FeedHealthResource {
    static final String NO_STATUS = "UNKNOWN";

    public record FeedState(String feedId, String state) {}

    public record Report(String status, List<FeedState> feeds) {}

    @Inject
    @All
    List<FeedAdapter> adapters;

    @Inject
    FeedStatusStore store;

    Clock clock = Clock.systemUTC();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Report whether every configured feed is OK")
    @APIResponse(responseCode = "200", description = "All feeds are OK")
    @APIResponse(responseCode = "503", description = "At least one feed is STALE, DOWN or has no status yet")
    public Response check() {
        List<FeedState> feeds = adapters.stream()
                .map(adapter -> new FeedState(adapter.id(), currentState(adapter.id())))
                .toList();
        boolean healthy = feeds.stream().allMatch(feed -> feed.state().equals(State.OK.name()));
        return Response.status(healthy ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(new Report(healthy ? "UP" : "DOWN", feeds))
                .build();
    }

    private String currentState(String feedId) {
        return store.find(feedId).map(this::stateOf).orElse(NO_STATUS);
    }

    /** Checks the age too, so a poller that stopped writing can't leave a feed looking OK forever (E03). */
    private String stateOf(FeedHealth health) {
        boolean overdue = health.lastSuccessAt() != null
                && Duration.between(health.lastSuccessAt(), clock.instant()).compareTo(FeedPoller.STALE_AFTER) > 0;
        return health.state() == State.OK && overdue ? State.STALE.name() : health.state().name();
    }
}
