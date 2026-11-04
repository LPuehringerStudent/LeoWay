package at.leoway.api.webuntis;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Encrypts WebUntis access keys with AES-256-GCM (S01). The key comes from {@code LEOWAY_KEY_ENCRYPTION_KEY}
 * (Base64, 32 bytes) and is never stored in the database or the repo. Stored value: 12-byte nonce + ciphertext.
 * The student id is bound as associated data, so a ciphertext copied to another student's row won't decrypt.
 */
@ApplicationScoped
public class KeyCipher {
    /** Stored in webuntis_credential.key_version; bump when rotating the key. */
    public static final int VERSION = 1;
    private static final int NONCE = 12;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public KeyCipher(@ConfigProperty(name = "leoway.key-encryption-key") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LEOWAY_KEY_ENCRYPTION_KEY is not Base64");
        }
        if (raw.length != 32) {
            throw new IllegalStateException("LEOWAY_KEY_ENCRYPTION_KEY must be 32 bytes (openssl rand -base64 32)");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String accessKey, long studentId) {
        byte[] nonce = new byte[NONCE];
        random.nextBytes(nonce);
        try {
            Cipher c = cipher(Cipher.ENCRYPT_MODE, nonce, studentId);
            byte[] ct = c.doFinal(accessKey.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(NONCE + ct.length).put(nonce).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed");
        }
    }

    public String decrypt(byte[] stored, long studentId) {
        try {
            Cipher c = cipher(Cipher.DECRYPT_MODE, java.util.Arrays.copyOf(stored, NONCE), studentId);
            return new String(c.doFinal(stored, NONCE, stored.length - NONCE), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("stored access key cannot be decrypted (wrong key or tampered)");
        }
    }

    private Cipher cipher(int mode, byte[] nonce, long studentId) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, key, new GCMParameterSpec(128, nonce));
        c.updateAAD(ByteBuffer.allocate(8).putLong(studentId).array());
        return c;
    }
}
