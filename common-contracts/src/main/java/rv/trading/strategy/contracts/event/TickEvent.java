package rv.trading.strategy.contracts.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TickEvent(
        long instrumentPairId,
        String symbol,
        BigDecimal lastPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        Instant timestamp
) {
}
