package rv.trading.strategy.engine.broker;

import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;

import java.math.BigDecimal;

public interface BrokerTradingAdapter {
    BrokerCode brokerCode();

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
