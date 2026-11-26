package rv.trading.strategy.engine.broker.bybit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public final class BybitV5Signer {
    public static final String HMAC_SHA256 = "HmacSHA256";

    private BybitV5Signer() {
    }

    public static String sign(String apiKey, String apiSecret, long timestamp,
                              long recvWindow, String queryStringOrJsonBody) {
        String payload = timestamp + apiKey + recvWindow +
                (queryStringOrJsonBody == null ? "" : queryStringOrJsonBody);
        return hmacSha256(payload, apiSecret);
    }

    public static String hmacSha256(String payload, String apiSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to sign Bybit V5 request", exception);
        }
    }
}
