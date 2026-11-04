package at.leoway.api.me;

import at.leoway.api.domain.Student;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
import org.jboss.logging.Logger;

@Path("/me")
@RolesAllowed(Roles.STUDENT)
public class MeResource {
    private static final Logger LOG = Logger.getLogger(MeResource.class);

    public record Me(String username, Instant createdAt) {}

    @Inject
    CurrentStudent current;

    @GET
    public Me me() {
        Student s = current.get();
        return new Me(s.username, s.createdAt);
    }

    /**
     * Deletes the account. The foreign keys cascade to the WebUntis key, routes and lateness
     * entries (L02, L03). Delay records are not personal data and stay.
     */
    @DELETE
    @Transactional
    public void delete() {
        long id = current.id();
        Student.deleteById(id);
        // Id only: usernames are personal data and stay out of the log.
        LOG.infof("account %d deleted", id);
    }
}
