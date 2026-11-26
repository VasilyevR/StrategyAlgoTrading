package rv.trading.strategy.contracts.crypto;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherTest {
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final String BLANK_CIPHERTEXT = " ";

    @Test
    void encryptsAndDecryptsPlaintextWithBase64Aes256Key() {
        byte[] key = key();
        String plaintext = Instancio.create(String.class);

        String ciphertext = AesGcmCipher.encrypt(plaintext, key);

        assertThat(AesGcmCipher.decrypt(ciphertext, key)).isEqualTo(plaintext);
    }

    @Test
    void generatesUniqueIvForEachEncryption() {
        byte[] key = key();
        String first = AesGcmCipher.encrypt(Instancio.create(String.class), key);
        String second = AesGcmCipher.encrypt(Instancio.create(String.class), key);
        byte[] firstIv = Arrays.copyOf(Base64.getDecoder().decode(first), IV_LENGTH_BYTES);
        byte[] secondIv = Arrays.copyOf(Base64.getDecoder().decode(second), IV_LENGTH_BYTES);

        assertThat(firstIv).isNotEqualTo(secondIv);
    }

    @Test
    void rejectsInvalidKeyLengthsAndInvalidCiphertexts() {
        byte[] key = key();
        String ciphertext = AesGcmCipher.encrypt(Instancio.create(String.class), key);
        byte[] tamperedBytes = Base64.getDecoder().decode(ciphertext);
        tamperedBytes[tamperedBytes.length - 1] ^= 0x01;
        String tamperedCiphertext = Base64.getEncoder().encodeToString(tamperedBytes);

        assertThatThrownBy(() -> AesGcmCipher.decodeBase64Key(Base64.getEncoder()
                .encodeToString(new byte[KEY_LENGTH_BYTES / 2])))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCipher.decrypt(BLANK_CIPHERTEXT, key))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCipher.decrypt(tamperedCiphertext, key))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmCipher.decrypt(ciphertext, new byte[KEY_LENGTH_BYTES]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] key() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        for (int index = 0; index < key.length; index++) {
            key[index] = (byte) index;
        }
        return AesGcmCipher.decodeBase64Key(Base64.getEncoder().encodeToString(key));
    }
}
