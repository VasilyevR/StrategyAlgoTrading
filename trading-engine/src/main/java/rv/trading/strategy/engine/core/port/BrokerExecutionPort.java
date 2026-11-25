package rv.trading.strategy.engine.core.port;

import java.math.BigDecimal;

public interface BrokerExecutionPort {
    BigDecimal getAvailableBalance(long brokerClientId, long instrumentPairId);

    OrderExecutionReceipt placeOrder(PlaceOrderCommand command);

    void amendStopLoss(AmendStopLossCommand command);

    void cancelOrder(long brokerClientId, long instrumentPairId, String exchangeOrderId);
}
