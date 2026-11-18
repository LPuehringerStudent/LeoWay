package at.leoway.api.me;

import at.leoway.api.domain.SavedRoute;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** The student's saved routes. Shows the ownership pattern every private resource follows (S02). */
@Path("/me/routes")
@RolesAllowed(Roles.STUDENT)
@Tag(name = "Routes", description = "The student's saved routes. Other students' routes always look missing (404).")
public class RouteResource {
    public record Route(long id, String homeStopId, String schoolStopId,
                        int walkToStopMinutes, int walkToSchoolMinutes, int bufferMinutes) {
        static Route of(SavedRoute r) {
            return new Route(r.id, r.homeStopId, r.schoolStopId,
                    r.walkToStopMinutes, r.walkToSchoolMinutes, r.bufferMinutes);
        }
    }

    /**
     * What the route editor sends (F02). Stops are ids from {@code GET /api/stops}, never an address (L02).
     * Walking times and buffer are whole minutes; a missing buffer means the suggested 5 minutes.
     */
    public record RouteInput(
            @Schema(required = true, description = "Stop id from GET /api/stops", examples = "60501720")
            String homeStopId,
            @Schema(required = true, description = "Stop id from GET /api/stops", examples = "60500120")
            String schoolStopId,
            @Schema(required = true, minimum = "0", maximum = "60") Integer walkToStopMinutes,
            @Schema(required = true, minimum = "0", maximum = "60") Integer walkToSchoolMinutes,
            @Schema(minimum = "0", maximum = "30", defaultValue = "5") Integer bufferMinutes) {}

    static final int MAX_WALK_MINUTES = 60;
    static final int MAX_BUFFER_MINUTES = 30;
    static final int DEFAULT_BUFFER_MINUTES = 5;
    /** Stop ids are short tokens (Linz AG: digits, GTFS: e.g. at:44:41164). Rules out free text like an address. */
    private static final Pattern STOP_ID = Pattern.compile("[0-9A-Za-z:._-]{1,40}");

    @Inject
    CurrentStudent current;

    @GET
    @Operation(summary = "List the student's saved routes")
    public List<Route> list() {
        return SavedRoute.<SavedRoute>list("studentId", current.id()).stream().map(Route::of).toList();
    }

    @GET
    @Operation(summary = "Show one saved route")
    @APIResponse(responseCode = "404", description = "No such route for this student")
    @Path("/{id}")
    public Route get(@PathParam("id") long id) {
        SavedRoute route = SavedRoute.findById(id);
        return Route.of(current.owned(route, route == null ? null : route.studentId));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Save a new route")
    @APIResponse(responseCode = "201", description = "Saved; Location points to the new route",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Route.class)))
    @APIResponse(responseCode = "400", description = "Invalid stops, walking times or buffer",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Errors.class)))
    @Transactional
    public Response create(RouteInput input, @Context UriInfo uri) {
        SavedRoute route = new SavedRoute();
        route.studentId = current.id();
        apply(route, input);
        route.persist();
        return Response.created(uri.getAbsolutePathBuilder().path(String.valueOf(route.id)).build())
                .entity(Route.of(route)).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Change one saved route")
    @APIResponse(responseCode = "200", description = "Changed",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Route.class)))
    @APIResponse(responseCode = "400", description = "Invalid stops, walking times or buffer",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Errors.class)))
    @APIResponse(responseCode = "404", description = "No such route for this student")
    @Path("/{id}")
    @Transactional
    public Route update(@PathParam("id") long id, RouteInput input) {
        SavedRoute route = SavedRoute.findById(id);
        current.owned(route, route == null ? null : route.studentId);
        apply(route, input);
        return Route.of(route);
    }

    /** Validates everything first and reports all problems at once, so the editor can show them together. */
    private static void apply(SavedRoute route, RouteInput input) {
        if (input == null) throw invalid(List.of("route body is missing"));
        List<String> errors = new ArrayList<>();
        String home = stopId(input.homeStopId(), "homeStopId", errors);
        String school = stopId(input.schoolStopId(), "schoolStopId", errors);
        if (home != null && home.equals(school)) errors.add("homeStopId and schoolStopId must differ");
        minutes(input.walkToStopMinutes(), "walkToStopMinutes", MAX_WALK_MINUTES, errors);
        minutes(input.walkToSchoolMinutes(), "walkToSchoolMinutes", MAX_WALK_MINUTES, errors);
        int buffer = input.bufferMinutes() == null ? DEFAULT_BUFFER_MINUTES : input.bufferMinutes();
        minutes(buffer, "bufferMinutes", MAX_BUFFER_MINUTES, errors);
        if (!errors.isEmpty()) throw invalid(errors);
        route.homeStopId = home;
        route.schoolStopId = school;
        route.walkToStopMinutes = input.walkToStopMinutes();
        route.walkToSchoolMinutes = input.walkToSchoolMinutes();
        route.bufferMinutes = buffer;
    }

    /** Every problem with the input, in field order. */
    public record Errors(List<String> errors) {}

    private static BadRequestException invalid(List<String> errors) {
        return new BadRequestException(Response.status(400).type(MediaType.APPLICATION_JSON)
                .entity(new Errors(errors)).build());
    }

    private static String stopId(String value, String field, List<String> errors) {
        String id = value == null ? null : value.strip();
        if (id == null || !STOP_ID.matcher(id).matches()) {
            errors.add(field + " must be a stop id from GET /api/stops");
            return null;
        }
        return id;
    }

    private static void minutes(Integer value, String field, int max, List<String> errors) {
        if (value == null || value < 0 || value > max) errors.add(field + " must be 0 to " + max + " minutes");
    }

    @DELETE
    @Operation(summary = "Delete one saved route")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "No such route for this student")
    @Path("/{id}")
    @Transactional
    public void delete(@PathParam("id") long id) {
        SavedRoute route = SavedRoute.findById(id);
        current.owned(route, route == null ? null : route.studentId).delete();
    }
}
