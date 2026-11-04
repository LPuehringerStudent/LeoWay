package at.leoway.api.security;

import at.leoway.api.domain.Student;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

/**
 * Resolves the logged-in student. Every private query goes through {@link #id()} or
 * {@link #owned}, so a student can only ever reach their own rows (S02, A13).
 *
 * <p>Independent of the login mechanism: whatever authenticates the request must put the
 * student's username in the principal and the {@code student} role in the identity.
 */
@RequestScoped
public class CurrentStudent {
    @Inject
    SecurityIdentity identity;

    private Student student;

    public Student get() {
        if (student == null) {
            if (identity.isAnonymous()) {
                throw new UnauthorizedException();
            }
            if (!identity.hasRole(Roles.STUDENT)) {
                throw new ForbiddenException();
            }
            student = Student.<Student>find("username", identity.getPrincipal().getName())
                    .firstResultOptional()
                    // Logged in, but the account was deleted: treat like a stranger.
                    .orElseThrow(ForbiddenException::new);
        }
        return student;
    }

    public long id() {
        return get().id;
    }

    /**
     * Returns the row if it belongs to the current student. Someone else's row and a missing row
     * both give 404, so ids of other students' data can't be probed.
     */
    public <T> T owned(T row, Long ownerId) {
        if (row == null || ownerId == null || ownerId != id()) {
            throw new NotFoundException();
        }
        return row;
    }
}
