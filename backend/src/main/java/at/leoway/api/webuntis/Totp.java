package at.leoway.api.webuntis;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * One-time codes from a WebUntis access key (RFC 6238: SHA-1, 6 digits, 30 s), so LeoWay never needs the
 * student's password. The access key is the Base32 secret shown under WebUntis → Profil → Freigaben.
 */
public final class Totp {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {}

    public static String code(String base32Secret, Instant time) {
        return code(decodeBase32(base32Secret), time.getEpochSecond() / 30);
    }

    static String code(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    /** Decodes Base32, ignoring spaces, dashes, padding and case (keys are often shown in groups). */
    static byte[] decodeBase32(String s) {
        String clean = s.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) {
            throw new IllegalArgumentException("access key is empty");
        }
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                // Never include the key in the message.
                throw new IllegalArgumentException("access key contains an invalid character");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
