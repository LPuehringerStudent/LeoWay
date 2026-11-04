package at.leoway.api.admin;

import at.leoway.api.domain.FeedStatus;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

/** Feed health for administrators. Admins get this and health checks only, no student data (S02). */
@Path("/admin/feeds")
@RolesAllowed(Roles.ADMIN)
@Tag(name = "Admin", description = "Feed health for administrators. No student data.")
public class AdminFeedResource {
    @GET
    @Operation(summary = "List all feeds with their health state (OK, STALE, DOWN)")
    @APIResponse(responseCode = "200", description = "Feed states")
    @APIResponse(responseCode = "401", description = "Not logged in")
    @APIResponse(responseCode = "403", description = "Not an administrator")
    public List<FeedStatus> feeds() {
        return FeedStatus.listAll();
    }
}
