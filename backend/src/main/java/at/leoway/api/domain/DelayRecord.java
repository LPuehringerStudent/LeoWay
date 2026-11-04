package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.Immutable;

/** One observation of a departure. Rows are only ever added (S04). */
@Entity
@Immutable
@Table(name = "delay_record")
public class DelayRecord extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String source;

    @Column(nullable = false)
    public String line;

    @Column(name = "trip_id", nullable = false)
    public String tripId;

    @Column(name = "stop_id", nullable = false)
    public String stopId;

    public String bay;

    @Column(name = "service_date", nullable = false)
    public LocalDate serviceDate;

    @Column(name = "planned_at", nullable = false)
    public Instant plannedAt;

    /** Minutes, can be negative. Null means the departure has no real-time data. */
    @Column(name = "reported_delay")
    public Integer reportedDelay;

    @Column(name = "trip_status")
    public String tripStatus;

    @Column(name = "collected_at", nullable = false)
    public Instant collectedAt;
}
