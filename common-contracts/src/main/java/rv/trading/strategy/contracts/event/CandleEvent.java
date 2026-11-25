package rv.trading.strategy.contracts.event;

import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleEvent(
        long instrumentPairId,
        String symbol,
        TimeframeCode timeframeCode,
        Instant openTime,
        Instant closeTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean closed
) {
}
