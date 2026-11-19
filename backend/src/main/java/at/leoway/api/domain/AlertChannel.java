package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** A student's alert channel (#62, F04). The target is ciphertext only (S01). */
@Entity
@Table(name = "alert_channel")
@IdClass(AlertChannel.Key.class)
public class AlertChannel extends PanacheEntityBase {
    public enum Kind { DISCORD }

    /** Composite id (student, kind). A class, not a record: JPA id classes need a no-arg constructor and setters. */
    public static class Key implements Serializable {
        public Long studentId;
        public Kind kind;

        public Key() {}

        public Key(Long studentId, Kind kind) {
            this.studentId = studentId;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && java.util.Objects.equals(studentId, k.studentId) && kind == k.kind;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(studentId, kind);
        }
    }

    @Id
    @Column(name = "student_id")
    public Long studentId;

    @Id
    @Enumerated(EnumType.STRING)
    public Kind kind;

    @Column(name = "encrypted_target", nullable = false)
    public byte[] encryptedTarget;

    @Column(name = "key_version", nullable = false)
    public int keyVersion;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    /** When the channel rejected a message for good, or null while it works. */
    @Column(name = "broken_at")
    public Instant brokenAt;

    public static AlertChannel find(long studentId, Kind kind) {
        return findById(new Key(studentId, kind));
    }
}
