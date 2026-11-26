package rv.trading.strategy.collector.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rv.trading.strategy.collector.service.CandleStorageService;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;

import java.util.List;

@Validated
@RestController
public class InternalCandleController {

    public static final String CANDLES_PATH = "/internal/candles";
    public static final String INSTRUMENT_PAIR_ID_PARAM = "instrumentPairId";
    public static final String TIMEFRAME_CODE_PARAM = "timeframeCode";
    public static final String LIMIT_PARAM = "limit";
    public static final String DEFAULT_LIMIT = "200";
    public static final int MAX_LIMIT = 1000;

    private final CandleStorageService candleStorageService;

    public InternalCandleController(CandleStorageService candleStorageService) {
        this.candleStorageService = candleStorageService;
    }

    @GetMapping(CANDLES_PATH)
    public List<CandleEvent> getCandles(
            @RequestParam(INSTRUMENT_PAIR_ID_PARAM) @Positive long instrumentPairId,
            @RequestParam(TIMEFRAME_CODE_PARAM) TimeframeCode timeframeCode,
            @RequestParam(name = LIMIT_PARAM, defaultValue = DEFAULT_LIMIT) @Min(1) @Max(MAX_LIMIT) int limit
    ) {
        return candleStorageService.loadRecentCandles(instrumentPairId, timeframeCode, limit);
    }
}
