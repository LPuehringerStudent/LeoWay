package at.leoway.api.feed;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/admin/feeds")
@RolesAllowed("admin")
public class AdminFeedsResource {
    @Inject
    FeedStatusStore store;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<FeedHealth> list() {
        return store.findAll();
    }
}
