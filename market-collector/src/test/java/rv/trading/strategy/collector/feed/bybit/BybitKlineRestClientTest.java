package rv.trading.strategy.collector.feed.bybit;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class BybitKlineRestClientTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final int LIMIT = 2;
    private static final long INSTRUMENT_PAIR_ID = 101L;
    private static final String RESPONSE_CONTENT_TYPE = "application/json";

    private static final WireMockServer WIRE_MOCK = new WireMockServer(
            com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort());

    @BeforeEach
    void resetWireMock() {
        if (!WIRE_MOCK.isRunning()) {
            WIRE_MOCK.start();
        }
        WIRE_MOCK.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @Test
    void fetchesReverseChronologicalRowsAndReturnsExactChronologicalCandles() {
        Instant minuteBoundary = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant pastOpenTime = minuteBoundary.minus(10, ChronoUnit.MINUTES);
        Instant futureOpenTime = minuteBoundary.plus(5, ChronoUnit.MINUTES);
        WIRE_MOCK.stubFor(get(urlPathEqualTo(BybitConstants.REST_KLINE_PATH))
                .withQueryParam(BybitConstants.SYMBOL_KEY, equalTo(SYMBOL))
                .withQueryParam(BybitConstants.INTERVAL_KEY, equalTo(BybitConstants.INTERVAL_1_MINUTE))
                .withQueryParam(BybitConstants.LIMIT_KEY, equalTo(Integer.toString(LIMIT)))
                .withQueryParam(BybitConstants.CATEGORY_KEY, equalTo(BybitConstants.SPOT_CATEGORY))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withHeader("Content-Type", RESPONSE_CONTENT_TYPE)
                        .withBody(klineResponse(futureOpenTime, pastOpenTime))));

        List<CandleEvent> candles = createClient().fetchHistoricalKlines(
                INSTRUMENT_PAIR_ID, SYMBOL, BybitConstants.SPOT_CATEGORY, TimeframeCode.M1, LIMIT);

        assertThat(candles).hasSize(LIMIT);
        assertThat(candles).extracting(CandleEvent::openTime).containsExactly(pastOpenTime, futureOpenTime);
        assertThat(candles.getFirst())
                .satisfies(candle -> {
                    assertThat(candle.instrumentPairId()).isEqualTo(INSTRUMENT_PAIR_ID);
                    assertThat(candle.symbol()).isEqualTo(SYMBOL);
                    assertThat(candle.timeframeCode()).isEqualTo(TimeframeCode.M1);
                    assertThat(candle.closeTime()).isEqualTo(pastOpenTime.plusSeconds(60));
                    assertThat(candle.open()).isEqualTo(new BigDecimal("100.12345678"));
                    assertThat(candle.high()).isEqualTo(new BigDecimal("101.23456789"));
                    assertThat(candle.low()).isEqualTo(new BigDecimal("99.01234567"));
                    assertThat(candle.close()).isEqualTo(new BigDecimal("100.87654321"));
                    assertThat(candle.volume()).isEqualTo(new BigDecimal("12.34567890"));
                    assertThat(candle.closed()).isTrue();
                });
        assertThat(candles.getLast().closed()).isFalse();
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo(BybitConstants.REST_KLINE_PATH))
                .withQueryParam(BybitConstants.SYMBOL_KEY, equalTo(SYMBOL))
                .withQueryParam(BybitConstants.INTERVAL_KEY, equalTo(BybitConstants.INTERVAL_1_MINUTE))
                .withQueryParam(BybitConstants.LIMIT_KEY, equalTo(Integer.toString(LIMIT)))
                .withQueryParam(BybitConstants.CATEGORY_KEY, equalTo(BybitConstants.SPOT_CATEGORY)));
    }

    @Test
    void defaultsBlankCategoryToLinear() {
        Instant pastOpenTime = Instant.now().minus(10, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
        WIRE_MOCK.stubFor(get(urlPathEqualTo(BybitConstants.REST_KLINE_PATH))
                .withQueryParam(BybitConstants.CATEGORY_KEY, equalTo(BybitConstants.LINEAR_CATEGORY))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withHeader("Content-Type", RESPONSE_CONTENT_TYPE)
                        .withBody(klineResponse(pastOpenTime, pastOpenTime))));

        List<CandleEvent> candles = createClient().fetchHistoricalKlines(
                INSTRUMENT_PAIR_ID, SYMBOL, " ", TimeframeCode.M1, 1);

        assertThat(candles).hasSize(2);
        WIRE_MOCK.verify(getRequestedFor(urlPathEqualTo(BybitConstants.REST_KLINE_PATH))
                .withQueryParam(BybitConstants.CATEGORY_KEY, equalTo(BybitConstants.LINEAR_CATEGORY)));
    }

    @Test
    void returnsEmptyWhenBybitReportsAnErrorOrNoRows() {
        WIRE_MOCK.stubFor(get(urlPathEqualTo(BybitConstants.REST_KLINE_PATH))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withHeader("Content-Type", RESPONSE_CONTENT_TYPE)
                        .withBody("{\"retCode\":10001,\"result\":{\"list\":[]}}")));
        assertThat(createClient().fetchHistoricalKlines(
                INSTRUMENT_PAIR_ID, SYMBOL, BybitConstants.LINEAR_CATEGORY, TimeframeCode.M1, LIMIT)).isEmpty();

        WIRE_MOCK.resetAll();
        WIRE_MOCK.stubFor(get(urlPathEqualTo(BybitConstants.REST_KLINE_PATH))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withHeader("Content-Type", RESPONSE_CONTENT_TYPE)
                        .withBody("{\"retCode\":0,\"result\":{\"list\":[]}}")));
        assertThat(createClient().fetchHistoricalKlines(
                INSTRUMENT_PAIR_ID, SYMBOL, BybitConstants.LINEAR_CATEGORY, TimeframeCode.M1, LIMIT)).isEmpty();
    }

    private BybitKlineRestClient createClient() {
        return new BybitKlineRestClient(RestClient.builder().baseUrl(WIRE_MOCK.baseUrl()).build());
    }

    private static String klineResponse(Instant latestOpenTime, Instant earliestOpenTime) {
        return """
                {"retCode":0,"result":{"list":[
                  ["%d","200.1","202.2","199.3","201.4","20.5"],
                  ["%d","100.12345678","101.23456789","99.01234567","100.87654321","12.34567890"]
                ]}}
                """.formatted(latestOpenTime.toEpochMilli(), earliestOpenTime.toEpochMilli());
    }
}
