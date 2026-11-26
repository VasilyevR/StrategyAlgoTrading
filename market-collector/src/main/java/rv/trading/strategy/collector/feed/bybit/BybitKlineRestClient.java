package rv.trading.strategy.collector.feed.bybit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import rv.trading.strategy.collector.config.MarketCollectorProperties;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.enums.TimeframeDurations;
import rv.trading.strategy.contracts.event.CandleEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class BybitKlineRestClient {

    private final RestClient restClient;

    @Autowired
    public BybitKlineRestClient(MarketCollectorProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBybitRestBaseUrl())
                .build();
    }

    public BybitKlineRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<CandleEvent> fetchHistoricalKlines(long instrumentPairId,
                                                   String symbol,
                                                   String marketCategory,
                                                   TimeframeCode timeframeCode,
                                                   int limit) {
        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BybitConstants.REST_KLINE_PATH)
                        .queryParam(BybitConstants.SYMBOL_KEY, symbol)
                        .queryParam(BybitConstants.INTERVAL_KEY,
                                BybitTimeframeMapper.map(timeframeCode))
                        .queryParam(BybitConstants.LIMIT_KEY, Math.max(1, limit))
                        .queryParam(BybitConstants.CATEGORY_KEY,
                                categoryOrDefault(marketCategory))
                        .build())
                .retrieve()
                .body(JsonNode.class);
        return parseKlines(response, instrumentPairId, symbol, timeframeCode);
    }

    private List<CandleEvent> parseKlines(JsonNode response,
                                          long instrumentPairId,
                                          String symbol,
                                          TimeframeCode timeframeCode) {
        List<CandleEvent> candles = new ArrayList<>();
        if (response == null || response.path(BybitConstants.RET_CODE_KEY).asInt(-1) != 0) {
            return candles;
        }
        JsonNode rows = response.path(BybitConstants.RESULT_KEY).path(BybitConstants.LIST_KEY);
        if (!rows.isArray()) {
            return candles;
        }
        for (JsonNode row : rows) {
            if (!row.isArray() || row.size() < 6) {
                continue;
            }
            Instant openTime = Instant.ofEpochMilli(Long.parseLong(row.get(0).asText()));
            BigDecimal open = new BigDecimal(row.get(1).asText());
            BigDecimal high = new BigDecimal(row.get(2).asText());
            BigDecimal low = new BigDecimal(row.get(3).asText());
            BigDecimal close = new BigDecimal(row.get(4).asText());
            BigDecimal volume = new BigDecimal(row.get(5).asText());
            Instant closeTime = openTime.plus(TimeframeDurations.duration(timeframeCode));
            boolean closed = !closeTime.isAfter(Instant.now());
            candles.add(new CandleEvent(
                    instrumentPairId,
                    symbol,
                    timeframeCode,
                    openTime,
                    closeTime,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    closed
            ));
        }
        candles.sort(Comparator.comparing(CandleEvent::openTime));
        return candles;
    }

    private static String categoryOrDefault(String marketCategory) {
        return marketCategory == null || marketCategory.isBlank()
                ? BybitConstants.LINEAR_CATEGORY
                : marketCategory;
    }

}
