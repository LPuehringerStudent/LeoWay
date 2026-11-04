package at.leoway.api.webuntis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.leoway.api.domain.Student;
import at.leoway.api.domain.WebUntisCredential;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** F01 / A01: a valid key is stored encrypted, a wrong key stores nothing. */
@QuarkusTest
class WebUntisConnectionTest {
    @Inject
    WebUntisConnection connection;

    FakeWebUntis fake;
    long studentId;

    @BeforeEach
    void setUp() throws Exception {
        fake = new FakeWebUntis(18765); // matches %test.leoway.webuntis.base-url
        studentId = QuarkusTransaction.requiringNew().call(() -> {
            Student s = new Student();
            s.username = "connect-test";
            s.persist();
            return s.id;
        });
    }

    @AfterEach
    void tearDown() {
        fake.close();
        QuarkusTransaction.requiringNew().run(() -> Student.delete("username", "connect-test"));
    }

    @Test
    void validKeyIsStoredEncryptedAndUsable() {
        connection.connect(studentId, FakeWebUntis.USER, FakeWebUntis.KEY);

        WebUntisCredential stored = QuarkusTransaction.requiringNew().call(() -> WebUntisCredential.findById(studentId));
        assertEquals(KeyCipher.VERSION, stored.keyVersion);
        assertFalse(new String(stored.encryptedAccessKey, StandardCharsets.ISO_8859_1).contains(FakeWebUntis.KEY));

        var first = connection.firstLesson(studentId, LocalDate.of(2026, 11, 5)).orElseThrow();
        assertEquals(LocalTime.of(8, 55), first.start());
        assertTrue(connection.isConnected(studentId));
    }

    @Test
    void rejectedKeyStoresNothing() {
        var e = assertThrows(WebUntisException.class, () -> connection.connect(studentId, "wrong.user", FakeWebUntis.KEY));
        assertEquals(WebUntisException.Reason.KEY_REJECTED, e.reason());
        assertNull(QuarkusTransaction.requiringNew().call(() -> WebUntisCredential.findById(studentId)));
    }

    @Test
    void notConnectedGivesEmpty() {
        assertTrue(connection.firstLesson(studentId, LocalDate.of(2026, 11, 5)).isEmpty());
    }
}
