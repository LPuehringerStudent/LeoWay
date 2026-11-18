package at.leoway.api.me;

import at.leoway.api.Errors;
import at.leoway.api.alerts.AlertSender;
import at.leoway.api.alerts.Alerts;
import at.leoway.api.domain.AlertChannel;
import at.leoway.api.security.CurrentStudent;
import at.leoway.api.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * The student's alert channels (#62, F04). Discord first. Targets are write-only: no response ever contains the
 * webhook URL or any part of it (S01).
 */
@Path("/me/alerts")
@RolesAllowed(Roles.STUDENT)
@Tag(name = "Alerts", description = "Where the student gets alerts (F04). Targets are never returned.")
public class AlertResource {
    public record DiscordInput(
            @Schema(required = true, description = "Webhook URL from the Discord channel's Integrations settings")
            String webhookUrl) {}

    @Inject
    CurrentStudent current;

    @Inject
    Alerts alerts;

    @GET
    @Operation(summary = "List the student's alert channels", description = "Working or broken; never the target.")
    public List<Alerts.Status> list() {
        return alerts.list(current.id());
    }

    @PUT
    @Path("/discord")
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Set the Discord webhook",
            description = "Sends a test message to it and only stores it if Discord accepts it. Replaces an older one.")
    @APIResponse(responseCode = "200", description = "Stored; the test message was sent")
    @APIResponse(responseCode = "400", description = "Not a Discord webhook URL, or Discord refused it",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Errors.class)))
    @APIResponse(responseCode = "429", description = "A test message was sent less than 10 s ago; see Retry-After")
    @APIResponse(responseCode = "503", description = "Discord can't be reached; nothing was stored")
    public Alerts.Status setDiscord(DiscordInput input) {
        try {
            return alerts.save(current.id(), AlertChannel.Kind.DISCORD, input == null ? null : input.webhookUrl());
        } catch (AlertSender.InvalidTarget e) {
            throw Errors.badRequest(e.getMessage());
        } catch (RuntimeException e) {
            throw mapped(e);
        }
    }

    @POST
    @Path("/{kind}/test")
    @Operation(summary = "Send the test message again")
    @APIResponse(responseCode = "200", description = "Sent")
    @APIResponse(responseCode = "404", description = "No such channel")
    @APIResponse(responseCode = "409", description = "The channel refused it and is now marked broken",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = Errors.class)))
    @APIResponse(responseCode = "429", description = "A test message was sent less than 10 s ago; see Retry-After")
    @APIResponse(responseCode = "503", description = "The channel can't be reached right now")
    public Alerts.Status test(@PathParam("kind") String kind) {
        try {
            return alerts.test(current.id(), kind(kind));
        } catch (RuntimeException e) {
            throw mapped(e);
        }
    }

    @DELETE
    @Path("/{kind}")
    @Operation(summary = "Remove an alert channel")
    @APIResponse(responseCode = "204", description = "Removed (or was not set)")
    public void remove(@PathParam("kind") String kind) {
        alerts.remove(current.id(), kind(kind));
    }

    private static AlertChannel.Kind kind(String kind) {
        try {
            return AlertChannel.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }

    private static RuntimeException mapped(RuntimeException e) {
        return switch (e) {
            case Alerts.NotSet n -> new NotFoundException();
            case Alerts.TooSoon t -> new WebApplicationException(Response.status(429)
                    .header("Retry-After", Math.max(1, (t.retryAfter.toMillis() + 999) / 1000)).build());
            case AlertSender.Rejected r -> new WebApplicationException(Response.status(409)
                    .type(MediaType.APPLICATION_JSON).entity(new Errors(List.of(
                            "Discord no longer accepts this webhook. Paste a new webhook URL."))).build());
            case AlertSender.Unavailable u -> new WebApplicationException(Response.status(503).build());
            default -> e;
        };
    }
}
