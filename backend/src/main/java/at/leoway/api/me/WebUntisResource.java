package at.leoway.api.me;

import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.webuntis.TimetableSync;
import at.leoway.api.webuntis.WebUntisConnection;
import at.leoway.api.webuntis.WebUntisException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.time.Instant;
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

    /**
     * {@code available} is false only if WebUntis could not be reached; then {@code firstLesson} is unknown. A key
     * WebUntis no longer accepts is not an outage: then {@code available} stays true and {@code needsReconnect} is true.
     * {@code needsReconnect} is true when WebUntis no longer accepts the stored key; the student logs in again.
     * {@code syncedAt} is when the first lesson was last read from WebUntis.
     */
    public record Status(boolean connected, String username, boolean available, FirstLesson firstLesson,
            boolean needsReconnect, Instant syncedAt) {}

    public record FirstLesson(LocalDate date, LocalTime start, String subject) {}

    @Inject
    CurrentStudent current;

    @Inject
    WebUntisConnection connection;

    @Inject
    TimetableSync sync;

    @GET
    @Operation(summary = "Connection status and the next first lesson (today or the next school day)",
            description = "Served from the last background sync; WebUntis is only asked if that is outdated (#59).")
    @APIResponse(responseCode = "200", description = "Status")
    public Status status() {
        long id = current.id();
        WebUntisCredential credential = WebUntisCredential.findById(id);
        if (credential == null) {
            return new Status(false, null, true, null, false, null);
        }
        String username = credential.webUntisUsername;
        if (credential.rejectedAt != null) {
            return new Status(true, username, true, null, true, null);
        }
        LocalDate today = LocalDate.now(SCHOOL_ZONE);
        FirstLessonCache cached = FirstLessonCache.findById(id);
        if (cached == null || outdated(cached, today)) {
            try {
                cached = sync.sync(id, today).orElse(null);
            } catch (WebUntisException e) {
                boolean rejected = e.reason() == WebUntisException.Reason.KEY_REJECTED;
                return new Status(true, username, rejected, null, rejected, null);
            }
        }
        if (cached == null) return new Status(false, null, true, null, false, null);
        return new Status(true, username, true, of(cached), false, cached.syncedAt);
    }

    /** A lesson from an earlier day, or "no lesson" found before today: the next sync would give something else. */
    static boolean outdated(FirstLessonCache c, LocalDate today) {
        if (c.lessonDate != null) return c.lessonDate.isBefore(today);
        return c.syncedAt.atZone(SCHOOL_ZONE).toLocalDate().isBefore(today);
    }

    @DELETE
    @Operation(summary = "Disconnect WebUntis: deletes the stored access key")
    @APIResponse(responseCode = "204", description = "Disconnected")
    public void disconnect() {
        connection.disconnect(current.id());
    }

    private static FirstLesson of(FirstLessonCache c) {
        if (c.lessonDate == null) return null;
        return new FirstLesson(c.lessonDate, c.startAt.atZone(SCHOOL_ZONE).toLocalTime(), c.subject);
    }
}
