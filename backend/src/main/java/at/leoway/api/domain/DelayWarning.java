package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A student's current delay warning for the next first lesson (#63, F03). One row per student; {@code resolvedAt}
 * is set when the connection is back in time. The alternative fields are all null if there is no alternative.
 */
@Entity
@Table(name = "delay_warning")
public class DelayWarning extends PanacheEntityBase {
    @Id
    @Column(name = "student_id")
    public Long studentId;

    @Column(name = "lesson_start", nullable = false)
    public Instant lessonStart;

    @Column(nullable = false)
    public String line;

    @Column(name = "trip_id", nullable = false)
    public String tripId;

    @Column(name = "planned_departure", nullable = false)
    public Instant plannedDeparture;

    @Column(name = "delay_minutes", nullable = false)
    public int delayMinutes;

    @Column(name = "at_school", nullable = false)
    public Instant atSchool;

    @Column(name = "minutes_late", nullable = false)
    public int minutesLate;

    @Column(nullable = false)
    public boolean exam;

    @Column(name = "alternative_line")
    public String alternativeLine;

    @Column(name = "alternative_departure")
    public Instant alternativeDeparture;

    @Column(name = "alternative_leave_at")
    public Instant alternativeLeaveAt;

    @Column(name = "alternative_at_school")
    public Instant alternativeAtSchool;

    @Column(name = "sent_at", nullable = false)
    public Instant sentAt;

    @Column(name = "resolved_at")
    public Instant resolvedAt;
}
