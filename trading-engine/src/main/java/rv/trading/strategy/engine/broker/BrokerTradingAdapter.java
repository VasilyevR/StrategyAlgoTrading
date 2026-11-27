package rv.trading.strategy.engine.broker;

import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;

import java.math.BigDecimal;

public interface BrokerTradingAdapter {
    BrokerCode brokerCode();

    default java.util.Optional<OrderExecutionReceipt> fetchOrderReceipt(
            DecryptedBrokerCredentials credentials, String symbol, String exchangeOrderId) {
        return java.util.Optional.empty();
    }

    BigDecimal getAvailableBalance(DecryptedBrokerCredentials credentials,
                                   long instrumentPairId);

    OrderExecutionReceipt placeOrder(DecryptedBrokerCredentials credentials,
                                     PlaceOrderCommand command);

    void amendStopLoss(DecryptedBrokerCredentials credentials,
                       AmendStopLossCommand command);

    void cancelOrder(DecryptedBrokerCredentials credentials,
                     long instrumentPairId,
                     String exchangeOrderId);
}
