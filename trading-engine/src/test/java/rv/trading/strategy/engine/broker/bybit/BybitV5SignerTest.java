package rv.trading.strategy.engine.broker.bybit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BybitV5SignerTest {
    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-api-secret";
    private static final long TIMESTAMP = 1_700_000_000_000L;
    private static final long RECV_WINDOW = 5_000L;
    private static final String GET_QUERY = "accountType=UNIFIED";
    private static final String POST_JSON = "{\"category\":\"linear\",\"symbol\":\"BTCUSDT\",\"qty\":\"1\"}";
    private static final String GET_SIGNATURE = "fc84a15262df25de8fc8b0e5c78856642cbd6f957ecc4acb169ba66b293d75e5";
    private static final String POST_SIGNATURE = "0d7346ab0598bd9baabc66273189f770eea3470c5bce51563bc9487a88ce241d";

    @Test
    void signsGetQueryAccordingToBybitV5PayloadSpecification() {
        assertThat(BybitV5Signer.sign(API_KEY, API_SECRET, TIMESTAMP, RECV_WINDOW, GET_QUERY))
                .isEqualTo(GET_SIGNATURE)
                .matches("[0-9a-f]{64}");
    }

    @Test
    void signsPostJsonBodyAccordingToBybitV5PayloadSpecification() {
        assertThat(BybitV5Signer.sign(API_KEY, API_SECRET, TIMESTAMP, RECV_WINDOW, POST_JSON))
                .isEqualTo(POST_SIGNATURE)
                .matches("[0-9a-f]{64}");
    }
}
