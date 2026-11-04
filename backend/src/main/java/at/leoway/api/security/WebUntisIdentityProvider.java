package at.leoway.api.security;

import at.leoway.api.domain.Student;
import at.leoway.api.webuntis.WebUntisClient;
import at.leoway.api.webuntis.WebUntisClientFactory;
import at.leoway.api.webuntis.WebUntisConnection;
import at.leoway.api.webuntis.WebUntisException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Login with the WebUntis username and access key (decision on #20). LeoWay has no passwords of its own.
 *
 * <p>Students: the key is checked with a real WebUntis login; on the first login the account is created and the
 * key stored encrypted, which also connects WebUntis (F01). Later logins refresh the stored key.
 * Administrators ({@code leoway.admins}) are checked the same way but get only the admin role and no stored data.
 */
@ApplicationScoped
public class WebUntisIdentityProvider implements IdentityProvider<UsernamePasswordAuthenticationRequest> {
    private static final Logger LOG = Logger.getLogger(WebUntisIdentityProvider.class);

    @Inject
    WebUntisConnection connection;

    @Inject
    WebUntisClientFactory clients;

    @ConfigProperty(name = "leoway.admins", defaultValue = " ")
    Set<String> admins;

    @Override
    public Class<UsernamePasswordAuthenticationRequest> getRequestType() {
        return UsernamePasswordAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(UsernamePasswordAuthenticationRequest request,
                                              AuthenticationRequestContext context) {
        String username = request.getUsername().trim().toLowerCase(Locale.ROOT);
        String key = new String(request.getPassword().getPassword());
        return context.runBlocking(() -> login(username, key));
    }

    SecurityIdentity login(String username, String key) {
        try {
            if (admins.contains(username)) {
                try (WebUntisClient client = clients.create()) {
                    client.login(username, key);
                }
                return identity(username, Roles.ADMIN);
            }
            QuarkusTransaction.requiringNew().run(() -> {
                Student student = Student.<Student>find("username", username).firstResult();
                if (student == null) {
                    student = new Student();
                    student.username = username;
                    student.persistAndFlush();
                }
                // Rolls the new account back if WebUntis rejects the key.
                connection.connect(student.id, username, key);
            });
            return identity(username, Roles.STUDENT);
        } catch (WebUntisException e) {
            // Reason only: never the username or key (S01).
            LOG.infof("login failed: %s", e.reason());
            throw new AuthenticationFailedException(e.reason().name());
        }
    }

    private static SecurityIdentity identity(String username, String role) {
        return QuarkusSecurityIdentity.builder().setPrincipal(new QuarkusPrincipal(username)).addRole(role).build();
    }
}
