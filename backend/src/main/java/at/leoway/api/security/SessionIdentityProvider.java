package at.leoway.api.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TrustedAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Restores the identity from the encrypted session cookie on every request, without calling WebUntis.
 * Roles are derived again from config, so removing someone from {@code leoway.admins} takes effect at once.
 * A deleted account keeps a valid cookie until it expires, but {@link CurrentStudent} then answers 403.
 */
@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<TrustedAuthenticationRequest> {
    @ConfigProperty(name = "leoway.admins", defaultValue = " ")
    Set<String> admins;

    @Override
    public Class<TrustedAuthenticationRequest> getRequestType() {
        return TrustedAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(TrustedAuthenticationRequest request, AuthenticationRequestContext context) {
        String username = request.getPrincipal();
        return Uni.createFrom().item(QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal(username))
                .addRole(admins.contains(username) ? Roles.ADMIN : Roles.STUDENT)
                .build());
    }
}
