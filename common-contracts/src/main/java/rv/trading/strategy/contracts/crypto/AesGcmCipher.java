package rv.trading.strategy.contracts.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public final class AesGcmCipher {
    public static final String AES_GCM_NO_PADDING = "AES/GCM/NoPadding";
    public static final String AES_KEY_ALGORITHM = "AES";
    public static final int AES_256_KEY_LENGTH_BYTES = 32;
    public static final int GCM_IV_LENGTH_BYTES = 12;
    public static final int GCM_TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmCipher() {
    }

    public static byte[] decodeBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("Base64 AES key must not be blank");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid Base64 AES key", exception);
        }
        if (key.length != AES_256_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES-256 key must contain exactly 32 bytes");
        }
        return key;
    }

    public static String encrypt(String plaintext, byte[] key) {
        validateKey(key);
        if (plaintext == null) {
            throw new IllegalArgumentException("Plaintext must not be null");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, AES_KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] encoded = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to encrypt plaintext", exception);
        }
    }

    public static String decrypt(String base64Ciphertext, byte[] key) {
        validateKey(key);
        if (base64Ciphertext == null || base64Ciphertext.isBlank()) {
            throw new IllegalArgumentException("Ciphertext must not be blank");
        }
        try {
            byte[] encoded = Base64.getDecoder().decode(base64Ciphertext);
            if (encoded.length < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) {
                throw new IllegalArgumentException("Ciphertext is too short");
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(encoded, 0, iv, 0, iv.length);
            byte[] ciphertext = new byte[encoded.length - iv.length];
            System.arraycopy(encoded, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(AES_GCM_NO_PADDING);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, AES_KEY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Failed to decrypt ciphertext", exception);
        }
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length != AES_256_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("AES-256 key must contain exactly 32 bytes");
        }
    }
}
