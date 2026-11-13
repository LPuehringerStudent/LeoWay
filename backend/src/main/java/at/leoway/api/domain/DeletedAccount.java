package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Id of an account its student deleted; re-applied after a backup restore (#55). No username on purpose. */
@Entity
@Table(name = "deleted_account")
public class DeletedAccount extends PanacheEntityBase {
    @Id
    @Column(name = "student_id")
    public Long studentId;

    @Column(name = "deleted_at", nullable = false)
    public Instant deletedAt = Instant.now();
}
