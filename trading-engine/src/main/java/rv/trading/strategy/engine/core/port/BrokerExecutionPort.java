package rv.trading.strategy.engine.core.port;

import java.math.BigDecimal;
import java.util.Optional;

public interface BrokerExecutionPort {

    default Optional<OrderExecutionReceipt> fetchOrderReceipt(
            long brokerClientId, long instrumentPairId, String symbol, String exchangeOrderId) {
        return java.util.Optional.empty();
    }

    BigDecimal getAvailableBalance(long brokerClientId, long instrumentPairId);

    OrderExecutionReceipt placeOrder(PlaceOrderCommand command);

    void amendStopLoss(AmendStopLossCommand command);

    void cancelOrder(long brokerClientId, long instrumentPairId, String exchangeOrderId);
}
