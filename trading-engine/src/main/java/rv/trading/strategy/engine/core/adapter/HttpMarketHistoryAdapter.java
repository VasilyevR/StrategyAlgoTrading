package rv.trading.strategy.engine.core.adapter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.engine.core.port.MarketHistoryPort;

import java.util.Arrays;
import java.util.List;

@Component
public class HttpMarketHistoryAdapter implements MarketHistoryPort {
    public static final String CANDLES_PATH = "/internal/candles";
    public static final String INSTRUMENT_PAIR_ID_PARAM = "instrumentPairId";
    public static final String TIMEFRAME_CODE_PARAM = "timeframeCode";
    public static final String LIMIT_PARAM = "limit";

    private final RestClient restClient;

    public HttpMarketHistoryAdapter(@Qualifier("marketCollectorRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<CandleEvent> loadRecentCandles(long instrumentPairId, TimeframeCode timeframeCode, int limit) {
        CandleEvent[] candles = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(CANDLES_PATH)
                        .queryParam(INSTRUMENT_PAIR_ID_PARAM, instrumentPairId)
                        .queryParam(TIMEFRAME_CODE_PARAM, timeframeCode)
                        .queryParam(LIMIT_PARAM, Math.max(1, limit))
                        .build())
                .retrieve()
                .body(CandleEvent[].class);
        return candles == null ? List.of() : Arrays.asList(candles);
    }
}
