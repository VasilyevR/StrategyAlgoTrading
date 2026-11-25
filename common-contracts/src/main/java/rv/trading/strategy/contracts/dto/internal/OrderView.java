package rv.trading.strategy.contracts.dto.internal;

import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderView(
        Long id,
        Long brokerClientId,
        Long instrumentPairId,
        Long positionId,
        Long algotradingId,
        Long signalId,
        Long parentOrderId,
        String externalOrderId,
        String clientOrderId,
        OrderSide side,
        OrderType orderType,
        TimeInForce timeInForce,
        boolean outsideRth,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal price,
        BigDecimal stopPrice,
        BigDecimal avgFillPrice,
        BigDecimal commission,
        String commissionCurrency,
        OrderStatus status,
        String rejectReason,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
