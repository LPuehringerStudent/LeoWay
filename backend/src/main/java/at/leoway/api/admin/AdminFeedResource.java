package at.leoway.api.admin;

import at.leoway.api.domain.FeedStatus;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.List;

/** Feed health for administrators. Admins get this and health checks only, no student data (S02). */
@Path("/admin/feeds")
@RolesAllowed(Roles.ADMIN)
public class AdminFeedResource {
    @GET
    public List<FeedStatus> feeds() {
        return FeedStatus.listAll();
    }
}
