package at.leoway.api.webuntis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KeyCipherTest {
    static final String K1 = "dGVzdC1rZXktdGVzdC1rZXktdGVzdC1rZXktMTIzNDU=";
    static final String K2 = "Y2ktb25seS1rZXktY2ktb25seS1rZXktY2ktb25seTE=";

    @Test
    void roundTripAndCiphertextHidesTheKey() {
        KeyCipher c = new KeyCipher(K1);
        byte[] stored = c.encrypt("JBSWY3DPEHPK3PXP", 1);
        assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("JBSWY3DP"));
        assertEquals("JBSWY3DPEHPK3PXP", c.decrypt(stored, 1));
        assertNotEquals(new String(stored, StandardCharsets.ISO_8859_1),
                new String(c.encrypt("JBSWY3DPEHPK3PXP", 1), StandardCharsets.ISO_8859_1), "fresh nonce each time");
    }

    @Test
    void wrongKeyOrOtherStudentFails() {
        byte[] stored = new KeyCipher(K1).encrypt("JBSWY3DPEHPK3PXP", 1);
        assertThrows(IllegalStateException.class, () -> new KeyCipher(K2).decrypt(stored, 1));
        assertThrows(IllegalStateException.class, () -> new KeyCipher(K1).decrypt(stored, 2));
    }

    @Test
    void rejectsShortKeys() {
        assertThrows(IllegalStateException.class, () -> new KeyCipher("c2hvcnQ="));
    }
}
