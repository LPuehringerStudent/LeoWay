package at.leoway.api.me;

import at.leoway.api.domain.DelayWarning;
import at.leoway.api.domain.FirstLessonCache;
import at.leoway.api.domain.SavedRoute;
import at.leoway.api.domain.WebUntisCredential;
import at.leoway.api.leaveby.Connection;
import at.leoway.api.leaveby.ConnectionSource;
import at.leoway.api.leaveby.LeaveBy;
import at.leoway.api.leaveby.LeaveByCalculator;
import at.leoway.api.leaveby.LiveDelays;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import at.leoway.api.webuntis.TimetableSync;
import at.leoway.api.webuntis.WebUntisException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * When to leave home for the next first lesson (F02, spec 5.4, A02). Times are stored and calculated in UTC and sent
 * in Vienna time with their offset, so the frontend shows them as they are.
 */
@Path("/me/leave-by")
@RolesAllowed(Roles.STUDENT)
@ApplicationScoped
@Tag(name = "Leave-by", description = "When the student has to leave home (F02).")
public class LeaveByResource {

    @Schema(name = "LeaveByStatus", description = """
            IN_TIME: the latest connection that arrives in time, buffer included.
            NO_BUFFER: none leaves room for the buffer; this one still arrives by the lesson start.
            LATE: every connection arrives after the lesson starts; this one arrives earliest (minutesLate).
            NO_CONNECTION: no connection that can still be reached.
            NO_ROUTE, NO_LESSON (none in the next week), NOT_CONNECTED, NEEDS_RECONNECT (WebUntis key rejected):
            nothing to calculate.""")
    public enum Status { IN_TIME, NO_BUFFER, LATE, NO_CONNECTION, NO_ROUTE, NO_LESSON, NOT_CONNECTED, NEEDS_RECONNECT }

    /**
     * @param line every line of the trip, "3 > 19" with a change
     * @param departure expected departure from the home stop: planned plus live delay
     */
    @Schema(name = "LeaveByConnection")
    public record Ride(String line, OffsetDateTime plannedDeparture, OffsetDateTime departure,
            OffsetDateTime plannedArrival, OffsetDateTime arrival) {}

    /**
     * @param source PREDICTION if the times come from real-time data (also when that data says "on time"),
     *               TIMETABLE if there was none (U03)
     */
    @Schema(name = "LeaveByResult")
    public record Result(Status status, Long routeId, OffsetDateTime lessonStart, OffsetDateTime leaveAt,
            OffsetDateTime atSchool, Ride connection, LeaveBy.Source source, Long minutesLate, Warning warning) {
        static Result nothing(Status status, Long routeId, OffsetDateTime lessonStart) {
            return new Result(status, routeId, lessonStart, null, null, null, null, null, null);
        }

        Result withWarning(Warning w) {
            return new Result(status, routeId, lessonStart, leaveAt, atSchool, connection, source, minutesLate, w);
        }
    }

    /**
     * The open delay warning for this lesson (F03, #63), checked in the background after recorder polls. Only for
     * the first saved route, the one the warnings watch.
     *
     * @param line the connection the timetable chose, now late
     * @param delayMinutes its live delay
     * @param atSchool when the student would reach school with it
     * @param exam an exam is in the first lesson
     * @param alternative another connection, null if none helps
     */
    @Schema(name = "DelayWarning")
    public record Warning(String line, OffsetDateTime plannedDeparture, int delayMinutes, OffsetDateTime atSchool,
            int minutesLate, boolean exam, Alternative alternative) {}

    /** @param inTime true if it reaches school by the lesson start, false if it only arrives earlier */
    @Schema(name = "DelayWarningAlternative")
    public record Alternative(String line, OffsetDateTime departure, OffsetDateTime leaveAt, OffsetDateTime atSchool,
            boolean inTime) {}

    @Inject
    CurrentStudent current;

    @Inject
    TimetableSync sync;

    @Inject
    ConnectionSource connections;

    @Inject
    LiveDelays liveDelays;

    private Clock clock = Clock.systemUTC();

    /** For tests. A method, because tests reach the bean through a client proxy. */
    void useClock(Clock c) {
        clock = c;
    }

    @GET
    @Operation(summary = "Leave-by time for the next first lesson",
            description = "Uses the student's first saved route unless routeId is given. The lesson comes from the "
                    + "last timetable sync (#59); connections from Linz AG, with real-time data close to the trip.")
    @APIResponse(responseCode = "200", description = "The result; see status")
    @APIResponse(responseCode = "404", description = "routeId is not one of the student's routes")
    @APIResponse(responseCode = "503", description = "WebUntis or the connection source can't be reached; "
            + "Retry-After (seconds) if the source is only busy")
    public Result leaveBy(@QueryParam("routeId") @Parameter(description = "Saved route; default the first one")
            Long routeId) {
        long id = current.id();
        SavedRoute route = route(id, routeId);
        Long rid = route == null ? null : route.id;

        WebUntisCredential credential = WebUntisCredential.findById(id);
        if (credential == null) return Result.nothing(Status.NOT_CONNECTED, rid, null);
        if (credential.rejectedAt != null) return Result.nothing(Status.NEEDS_RECONNECT, rid, null);
        FirstLessonCache lesson;
        try {
            lesson = nextLesson(id);
        } catch (WebUntisException e) {
            if (e.reason() == WebUntisException.Reason.KEY_REJECTED) {
                return Result.nothing(Status.NEEDS_RECONNECT, rid, null);
            }
            throw unavailable(null);
        }
        if (lesson == null || lesson.startAt == null) return Result.nothing(Status.NO_LESSON, rid, null);
        OffsetDateTime lessonStart = vienna(lesson.startAt);
        if (route == null) return Result.nothing(Status.NO_ROUTE, null, lessonStart);

        LeaveByCalculator.Walks walks = LeaveByCalculator.Walks.of(route);
        List<Connection> found;
        try {
            found = connections.arrivingBy(route.homeStopId, route.schoolStopId,
                    lesson.startAt.minus(Duration.ofMinutes(walks.toSchool())));
        } catch (ConnectionSource.Unavailable e) {
            throw unavailable(e.retryAfter);
        }
        LeaveBy r = LeaveByCalculator.calculate(lesson.startAt, walks,
                liveDelays.complete(found, route.homeStopId, route.schoolStopId), clock.instant());
        Warning warning = routeId == null ? warning(id, lesson.startAt) : null;
        Result result = switch (r) {
            case LeaveBy.InTime t -> result(Status.IN_TIME, rid, lessonStart, t.leaveAt(), t.atSchool(),
                    t.connection(), t.source(), null);
            case LeaveBy.NoBuffer t -> result(Status.NO_BUFFER, rid, lessonStart, t.leaveAt(), t.atSchool(),
                    t.connection(), t.source(), null);
            case LeaveBy.Late t -> result(Status.LATE, rid, lessonStart, t.leaveAt(), t.atSchool(),
                    t.connection(), t.source(), t.minutesLate());
            case LeaveBy.NoConnection n -> Result.nothing(Status.NO_CONNECTION, rid, lessonStart);
        };
        return result.withWarning(warning);
    }

    private static Warning warning(long studentId, Instant lessonStart) {
        DelayWarning w = DelayWarning.findById(studentId);
        if (w == null || w.resolvedAt != null || !w.lessonStart.equals(lessonStart)) return null;
        Alternative alternative = w.alternativeLine == null ? null : new Alternative(w.alternativeLine,
                vienna(w.alternativeDeparture), vienna(w.alternativeLeaveAt), vienna(w.alternativeAtSchool),
                !w.alternativeAtSchool.isAfter(lessonStart));
        return new Warning(w.line, vienna(w.plannedDeparture), w.delayMinutes, vienna(w.atSchool), w.minutesLate,
                w.exam, alternative);
    }

    private SavedRoute route(long studentId, Long routeId) {
        if (routeId == null) {
            return SavedRoute.<SavedRoute>find("studentId = ?1 order by id", studentId).firstResult();
        }
        SavedRoute route = SavedRoute.findById(routeId);
        // Another student's route looks missing, like in RouteResource (S02).
        if (route == null || route.studentId != studentId) throw new NotFoundException();
        return route;
    }

    /**
     * The stored next first lesson, synced again if it is outdated or has already started: then the student asks
     * about the next school day, so the sync starts tomorrow.
     */
    private FirstLessonCache nextLesson(long studentId) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(LeaveBy.VIENNA).toLocalDate();
        FirstLessonCache c = FirstLessonCache.findById(studentId);
        if (c != null && c.startAt != null && !c.startAt.isAfter(now)) {
            return sync.sync(studentId, today.plusDays(1)).orElse(null);
        }
        if (c == null || WebUntisResource.outdated(c, today)) return sync.sync(studentId, today).orElse(null);
        return c;
    }

    private static Result result(Status status, Long routeId, OffsetDateTime lessonStart, Instant leaveAt,
            Instant atSchool, Connection c, LeaveBy.Source source, Long minutesLate) {
        Ride ride = new Ride(c.line(), vienna(c.plannedDeparture()), vienna(c.departure()),
                vienna(c.plannedArrival()), vienna(c.arrival()));
        return new Result(status, routeId, lessonStart, vienna(leaveAt), vienna(atSchool), ride, source, minutesLate,
                null);
    }

    private static OffsetDateTime vienna(Instant t) {
        return t.atZone(LeaveBy.VIENNA).toOffsetDateTime();
    }

    private static WebApplicationException unavailable(Duration retryAfter) {
        Response.ResponseBuilder res = Response.status(503);
        if (retryAfter != null) res.header("Retry-After", Math.max(1, (retryAfter.toMillis() + 999) / 1000));
        return new WebApplicationException(res.build());
    }
}
