package rv.trading.strategy.contracts.dto.internal;

import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record SignalView(
        Long id,
        Long algotradingId,
        Instant candleOpenTime,
        SignalType signalType,
        SignalStatus status,
        BigDecimal triggerPrice,
        Map<String, Object> indicatorSnapshot,
        String errorMessage,
        Instant createdAt
) {
}
