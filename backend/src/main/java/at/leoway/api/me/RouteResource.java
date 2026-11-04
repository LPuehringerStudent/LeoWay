package at.leoway.api.me;

import at.leoway.api.domain.SavedRoute;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.List;

/** The student's saved routes. Shows the ownership pattern every private resource follows (S02). */
@Path("/me/routes")
@RolesAllowed(Roles.STUDENT)
public class RouteResource {
    public record Route(long id, String homeStopId, String schoolStopId,
                        int walkToStopMinutes, int walkToSchoolMinutes, int bufferMinutes) {
        static Route of(SavedRoute r) {
            return new Route(r.id, r.homeStopId, r.schoolStopId,
                    r.walkToStopMinutes, r.walkToSchoolMinutes, r.bufferMinutes);
        }
    }

    @Inject
    CurrentStudent current;

    @GET
    public List<Route> list() {
        return SavedRoute.<SavedRoute>list("studentId", current.id()).stream().map(Route::of).toList();
    }

    @GET
    @Path("/{id}")
    public Route get(@PathParam("id") long id) {
        SavedRoute route = SavedRoute.findById(id);
        return Route.of(current.owned(route, route == null ? null : route.studentId));
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") long id) {
        SavedRoute route = SavedRoute.findById(id);
        current.owned(route, route == null ? null : route.studentId).delete();
    }
}
