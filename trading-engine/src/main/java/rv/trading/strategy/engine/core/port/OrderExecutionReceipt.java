package rv.trading.strategy.engine.core.port;

import rv.trading.strategy.contracts.enums.OrderStatus;

import java.math.BigDecimal;

public record OrderExecutionReceipt(
        String exchangeOrderId,
        OrderStatus status,
        BigDecimal filledQuantity,
        BigDecimal avgFillPrice,
        BigDecimal commission,
        String commissionAsset,
        String rejectReason
) {
}
