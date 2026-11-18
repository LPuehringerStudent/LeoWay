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
    void aPurposeKeepsSecretsApart() {
        // #62: a webhook copied into the access key column (or back) must not decrypt.
        KeyCipher c = new KeyCipher(K1);
        byte[] webhook = c.encrypt("123/token", 1, "alert:DISCORD");
        assertEquals("123/token", c.decrypt(webhook, 1, "alert:DISCORD"));
        assertThrows(IllegalStateException.class, () -> c.decrypt(webhook, 1));
        assertThrows(IllegalStateException.class, () -> c.decrypt(c.encrypt("JBSWY3DPEHPK3PXP", 1), 1, "alert:DISCORD"));
    }

    @Test
    void rejectsShortKeys() {
        assertThrows(IllegalStateException.class, () -> new KeyCipher("c2hvcnQ="));
    }
}
