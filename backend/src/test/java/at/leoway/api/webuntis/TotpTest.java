package at.leoway.api.webuntis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {
    // RFC 6238 appendix B, SHA-1 key; the RFC lists 8 digits, WebUntis uses the last 6.
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238Vectors() {
        assertEquals("287082", Totp.code(RFC_KEY, 59 / 30));
        assertEquals("081804", Totp.code(RFC_KEY, 1111111109L / 30));
        assertEquals("005924", Totp.code(RFC_KEY, 1234567890L / 30));
    }

    @Test
    void base32KeyGivesSameCodeAsRawKey() {
        // "GEZDGNBVGY3TQOJQ..." is RFC_KEY in Base32.
        String base32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertEquals("287082", Totp.code(base32, Instant.ofEpochSecond(59)));
        assertEquals("287082", Totp.code("gezd gnbv gy3t qojq gezd gnbv gy3t qojq", Instant.ofEpochSecond(59)));
    }

    @Test
    void invalidKeyMessageDoesNotContainTheKey() {
        var e = assertThrows(IllegalArgumentException.class, () -> Totp.code("SECRET!1", Instant.EPOCH));
        assertFalse(e.getMessage().contains("SECRET"));
    }
}
