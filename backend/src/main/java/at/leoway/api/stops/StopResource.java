package at.leoway.api.stops;

import at.leoway.api.stops.StopFinder.Stop;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/** Stop search for the route editor (F02). Needs no login: it only passes a stop name on to Linz AG. */
@Path("/stops")
@Tag(name = "Stops", description = "Public-transport stops from the Linz AG stop finder. Stops only, no addresses.")
public class StopResource {
    private static final Logger LOG = Logger.getLogger(StopResource.class);
    static final int MIN_QUERY = 2;
    static final int MAX_QUERY = 60;

    @Inject
    StopFinder finder;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Search stops by name, best matches first (at most 10)")
    @APIResponse(responseCode = "200", description = "Matching stops; empty if none match")
    @APIResponse(responseCode = "400", description = "q is missing, shorter than 2 or longer than 60 characters")
    @APIResponse(responseCode = "429", description = "Too many new searches right now; see Retry-After (seconds)")
    @APIResponse(responseCode = "503", description = "The Linz AG stop finder can't be reached")
    public List<Stop> search(@Parameter(description = "Part of the stop name, e.g. Meixnerkr") @QueryParam("q") String q) {
        String query = q == null ? "" : q.strip();
        if (query.length() < MIN_QUERY || query.length() > MAX_QUERY) {
            throw new BadRequestException("q must have " + MIN_QUERY + " to " + MAX_QUERY + " characters");
        }
        try {
            return finder.search(query);
        } catch (StopFinder.Busy e) {
            long seconds = Math.max(1, (e.retryAfter.toMillis() + 999) / 1000);
            throw new WebApplicationException(Response.status(429).header("Retry-After", seconds).build());
        } catch (IOException e) {
            LOG.warnf("Stop search failed: %s", e.getMessage());
            throw new ServiceUnavailableException("stop search unavailable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("stop search unavailable");
        }
    }
}
