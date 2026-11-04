package at.leoway.api.me;

import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import at.leoway.api.webuntis.Lesson;
import at.leoway.api.webuntis.WebUntisConnection;
import at.leoway.api.webuntis.WebUntisException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/** The student's WebUntis connection (F01). Connecting happens at login, see SessionResource. */
@Path("/me/webuntis")
@RolesAllowed(Roles.STUDENT)
@Tag(name = "WebUntis", description = "The logged-in student's WebUntis connection (F01).")
public class WebUntisResource {
    static final ZoneId SCHOOL_ZONE = ZoneId.of("Europe/Vienna");

    /** {@code available} is false if WebUntis could not be reached; then {@code firstLesson} is unknown. */
    public record Status(boolean connected, String username, boolean available, FirstLesson firstLesson) {}

    public record FirstLesson(LocalDate date, LocalTime start, String subject) {}

    @Inject
    CurrentStudent current;

    @Inject
    WebUntisConnection connection;

    @GET
    @Operation(summary = "Connection status and the next first lesson (today or the next school day)")
    @APIResponse(responseCode = "200", description = "Status")
    public Status status() {
        long id = current.id();
        WebUntisCredential credential = WebUntisCredential.findById(id);
        if (credential == null) {
            return new Status(false, null, true, null);
        }
        try {
            FirstLesson first = connection.nextFirstLesson(id, LocalDate.now(SCHOOL_ZONE))
                    .map(WebUntisResource::of).orElse(null);
            return new Status(true, credential.webUntisUsername, true, first);
        } catch (WebUntisException e) {
            return new Status(true, credential.webUntisUsername, false, null);
        }
    }

    @DELETE
    @Operation(summary = "Disconnect WebUntis: deletes the stored access key")
    @APIResponse(responseCode = "204", description = "Disconnected")
    public void disconnect() {
        connection.disconnect(current.id());
    }

    private static FirstLesson of(Lesson l) {
        return new FirstLesson(l.date(), l.start(), l.subject());
    }
}
