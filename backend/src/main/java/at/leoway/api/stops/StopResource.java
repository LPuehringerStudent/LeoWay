package at.leoway.api.stops;

import at.leoway.api.Errors;
import at.leoway.api.security.Roles;
import at.leoway.api.stops.StopFinder.Stop;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

/**
 * Stop search for accounts with student access (F02). Every uncached search uses the one request slot shared by
 * everyone (see {@link StopFinder}), so an anonymous script could otherwise keep it busy for all students (#90).
 */
@Path("/stops")
@RolesAllowed(Roles.STUDENT)
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
    @APIResponse(responseCode = "400", description = "q is missing, shorter than 2 or longer than 60 characters",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Errors.class)))
    @APIResponse(responseCode = "429", description = "Too many new searches right now; see Retry-After (seconds)")
    @APIResponse(responseCode = "503", description = "The Linz AG stop finder can't be reached")
    public List<Stop> search(@Parameter(description = "Part of the stop name, e.g. Meixnerkr") @QueryParam("q") String q) {
        String query = q == null ? "" : q.strip();
        if (query.length() < MIN_QUERY || query.length() > MAX_QUERY) {
            throw Errors.badRequest("q must have " + MIN_QUERY + " to " + MAX_QUERY + " characters");
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
