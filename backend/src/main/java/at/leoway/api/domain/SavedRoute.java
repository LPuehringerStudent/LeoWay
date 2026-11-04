package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "saved_route")
public class SavedRoute extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "student_id", nullable = false)
    public Long studentId;

    @Column(name = "home_stop_id", nullable = false)
    public String homeStopId;

    @Column(name = "school_stop_id", nullable = false)
    public String schoolStopId;

    @Column(name = "walk_to_stop_minutes", nullable = false)
    public int walkToStopMinutes;

    @Column(name = "walk_to_school_minutes", nullable = false)
    public int walkToSchoolMinutes;

    @Column(name = "buffer_minutes", nullable = false)
    public int bufferMinutes = 5;
}
