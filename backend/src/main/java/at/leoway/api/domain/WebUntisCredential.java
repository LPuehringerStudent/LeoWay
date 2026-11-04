package at.leoway.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webuntis_credential")
public class WebUntisCredential extends PanacheEntityBase {
    @Id
    @Column(name = "student_id")
    public Long studentId;

    @Column(name = "webuntis_username", nullable = false)
    public String webUntisUsername;

    /** Ciphertext only (S01). */
    @Column(name = "encrypted_access_key", nullable = false)
    public byte[] encryptedAccessKey;

    @Column(name = "key_version", nullable = false)
    public int keyVersion;
}
