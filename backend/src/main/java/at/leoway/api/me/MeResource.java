package at.leoway.api.me;

import at.leoway.api.domain.Student;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
import org.jboss.logging.Logger;

@Path("/me")
@RolesAllowed(Roles.STUDENT)
@Tag(name = "Account", description = "The logged-in student's own account.")
public class MeResource {
    private static final Logger LOG = Logger.getLogger(MeResource.class);

    public record Me(String username, Instant createdAt) {}

    @Inject
    CurrentStudent current;

    @GET
    @Operation(summary = "Show the logged-in student")
    @APIResponse(responseCode = "200", description = "The student")
    @APIResponse(responseCode = "401", description = "Not logged in")
    @APIResponse(responseCode = "403", description = "No student account, or an administrator")
    public Me me() {
        Student s = current.get();
        return new Me(s.username, s.createdAt);
    }

    /**
     * Deletes the account. The foreign keys cascade to the WebUntis key, routes and lateness
     * entries (L02, L03). Delay records are not personal data and stay.
     */
    @DELETE
    @Operation(summary = "Delete the account and all private data", description = "Removes the WebUntis key, saved routes and lateness entries (L02, L03).")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "401", description = "Not logged in")
    @APIResponse(responseCode = "403", description = "No student account, or an administrator")
    @Transactional
    public void delete() {
        long id = current.id();
        Student.deleteById(id);
        // Id only: usernames are personal data and stay out of the log.
        LOG.infof("account %d deleted", id);
    }
}
