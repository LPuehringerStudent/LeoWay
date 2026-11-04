package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "feed_status")
public class FeedStatus extends PanacheEntityBase {
    public enum State { OK, STALE, DOWN }

    @Id
    @Column(name = "feed_id")
    public String feedId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    public State state;

    @Column(name = "last_success_at")
    public Instant lastSuccessAt;

    @Column(name = "last_error_at")
    public Instant lastErrorAt;

    @Column(name = "last_error")
    public String lastError;
}
