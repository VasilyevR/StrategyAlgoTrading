package rv.trading.strategy.engine.core.adapter;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import rv.trading.strategy.contracts.InternalApiHeaders;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class HttpMarketHistoryAdapterTest {
    private static final String INTERNAL_TOKEN = "collector-shared-token";
    private static final long INSTRUMENT_PAIR_ID = 53L;
    private static final int CANDLE_LIMIT = 25;
    private static final String CANDLE_SYMBOL = "ETHUSDT";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private WireMockServer wireMock;
    private CandleEvent candle;
    private HttpMarketHistoryAdapter adapter;

    @BeforeEach
    void startWireMockAndAdapter() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        candle = Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(CandleEvent.class, "symbol"), CANDLE_SYMBOL)
                .set(Select.field(CandleEvent.class, "timeframeCode"), TimeframeCode.M5)
                .set(Select.field(CandleEvent.class, "openTime"), Instant.parse("2026-01-02T03:04:00Z"))
                .set(Select.field(CandleEvent.class, "closeTime"), Instant.parse("2026-01-02T03:09:00Z"))
                .set(Select.field(CandleEvent.class, "open"), new BigDecimal("100.1"))
                .set(Select.field(CandleEvent.class, "high"), new BigDecimal("101.2"))
                .set(Select.field(CandleEvent.class, "low"), new BigDecimal("99.8"))
                .set(Select.field(CandleEvent.class, "close"), new BigDecimal("100.9"))
                .set(Select.field(CandleEvent.class, "volume"), new BigDecimal("12.5"))
                .set(Select.field(CandleEvent.class, "closed"), true)
                .create();
        wireMock.stubFor(get(urlPathEqualTo(HttpMarketHistoryAdapter.CANDLES_PATH))
                .willReturn(aResponse().withStatus(200).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(JSON_MAPPER.writeValueAsString(List.of(candle)))));
        RestClient restClient = RestClient.builder()
                .baseUrl(wireMock.baseUrl())
                .defaultHeader(InternalApiHeaders.INTERNAL_TOKEN, INTERNAL_TOKEN)
                .build();
        adapter = new HttpMarketHistoryAdapter(restClient);
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void requestsCandlesWithQueryParametersAndDeserializesEvents() {
        List<CandleEvent> candles = adapter.loadRecentCandles(INSTRUMENT_PAIR_ID, TimeframeCode.M5, CANDLE_LIMIT);

        assertThat(candles).containsExactly(candle);
        wireMock.verify(getRequestedFor(urlPathEqualTo(HttpMarketHistoryAdapter.CANDLES_PATH))
                .withQueryParam(HttpMarketHistoryAdapter.INSTRUMENT_PAIR_ID_PARAM,
                        equalTo(Long.toString(INSTRUMENT_PAIR_ID)))
                .withQueryParam(HttpMarketHistoryAdapter.TIMEFRAME_CODE_PARAM, equalTo(TimeframeCode.M5.name()))
                .withQueryParam(HttpMarketHistoryAdapter.LIMIT_PARAM, equalTo(Integer.toString(CANDLE_LIMIT)))
                .withHeader(InternalApiHeaders.INTERNAL_TOKEN, equalTo(INTERNAL_TOKEN)));
    }
}
