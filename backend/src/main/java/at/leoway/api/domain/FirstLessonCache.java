package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** A student's next first real lesson as of the last sync (#59). Lesson fields are null if none was found. */
@Entity
@Table(name = "first_lesson")
public class FirstLessonCache extends PanacheEntityBase {
    @Id
    @Column(name = "student_id")
    public Long studentId;

    @Column(name = "lesson_date")
    public LocalDate lessonDate;

    @Column(name = "start_at")
    public Instant startAt;

    @Column(name = "end_at")
    public Instant endAt;

    public String subject;

    @Column(name = "synced_at", nullable = false)
    public Instant syncedAt;
}
