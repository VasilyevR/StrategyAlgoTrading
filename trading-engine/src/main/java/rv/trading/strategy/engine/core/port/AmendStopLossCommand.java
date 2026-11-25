package rv.trading.strategy.engine.core.port;

import java.math.BigDecimal;

public record AmendStopLossCommand(
        long brokerClientId,
        long instrumentPairId,
        String symbol,
        String exchangeOrderId,
        BigDecimal newStopPrice
) {
}
