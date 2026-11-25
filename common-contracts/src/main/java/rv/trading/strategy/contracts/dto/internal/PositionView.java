package rv.trading.strategy.contracts.dto.internal;

import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionView(
        Long id,
        Long brokerClientId,
        Long instrumentPairId,
        PositionSide side,
        PositionStatus status,
        BigDecimal quantity,
        BigDecimal avgEntryPrice,
        BigDecimal currentStopLoss,
        BigDecimal realizedPnl,
        BigDecimal totalCommission,
        String currency,
        Long version,
        Instant openedAt,
        Instant closedAt
) {
}
