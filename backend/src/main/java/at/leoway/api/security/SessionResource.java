package at.leoway.api.security;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.FormAuthenticationMechanism;
import at.leoway.api.webuntis.WebUntisClientFactory;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Logging in is {@code POST /j_security_check} (form fields {@code j_username}, {@code j_password} = WebUntis
 * access key), handled by Quarkus form authentication: 200 with the session cookie, or 401.
 */
@Path("/session")
@Tag(name = "Session", description = "Login: POST /j_security_check with j_username and j_password (the WebUntis access key).")
public class SessionResource {
    @Inject
    SecurityIdentity identity;

    @Inject
    WebUntisClientFactory webUntis;

    public record Reachability(boolean webUntisReachable) {}

    /** After a failed login: lets the app tell "key not accepted" from "WebUntis is down". */
    @GET
    @Path("/webuntis-reachable")
    @PermitAll
    @Operation(summary = "Whether WebUntis can be reached right now (no login needed)")
    public Reachability reachable() {
        return new Reachability(webUntis.reachable());
    }

    @POST
    @Path("/logout")
    @Authenticated
    @Operation(summary = "Log out: removes the session cookie")
    @APIResponse(responseCode = "204", description = "Logged out")
    @APIResponse(responseCode = "401", description = "Not logged in")
    public void logout() {
        FormAuthenticationMechanism.logout(identity);
    }
}
