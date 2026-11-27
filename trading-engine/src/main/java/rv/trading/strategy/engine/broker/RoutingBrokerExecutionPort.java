package rv.trading.strategy.engine.broker;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;

import java.math.BigDecimal;
import java.util.List;

@Primary
@Component
public class RoutingBrokerExecutionPort implements BrokerExecutionPort {
    private final BrokerCredentialsPort credentialsPort;
    private final List<BrokerTradingAdapter> adapters;

    public RoutingBrokerExecutionPort(BrokerCredentialsPort credentialsPort,
                                      List<BrokerTradingAdapter> adapters) {
        this.credentialsPort = credentialsPort;
        this.adapters = adapters;
    }

    @Override
    public java.util.Optional<OrderExecutionReceipt> fetchOrderReceipt(
            long brokerClientId, long instrumentPairId, String symbol, String exchangeOrderId) {
        DecryptedBrokerCredentials credentials = loadCredentials(brokerClientId);
        return adapterFor(credentials).fetchOrderReceipt(credentials, symbol, exchangeOrderId);
    }

    @Override
    public BigDecimal getAvailableBalance(long brokerClientId, long instrumentPairId) {
        DecryptedBrokerCredentials credentials = loadCredentials(brokerClientId);
        return adapterFor(credentials).getAvailableBalance(credentials, instrumentPairId);
    }

    @Override
    public OrderExecutionReceipt placeOrder(PlaceOrderCommand command) {
        DecryptedBrokerCredentials credentials = loadCredentials(command.brokerClientId());
        return adapterFor(credentials).placeOrder(credentials, command);
    }

    @Override
    public void amendStopLoss(AmendStopLossCommand command) {
        DecryptedBrokerCredentials credentials = loadCredentials(command.brokerClientId());
        adapterFor(credentials).amendStopLoss(credentials, command);
    }

    @Override
    public void cancelOrder(long brokerClientId, long instrumentPairId, String exchangeOrderId) {
        DecryptedBrokerCredentials credentials = loadCredentials(brokerClientId);
        adapterFor(credentials).cancelOrder(credentials, instrumentPairId, exchangeOrderId);
    }

    private DecryptedBrokerCredentials loadCredentials(long brokerClientId) {
        return credentialsPort.loadCredentials(brokerClientId)
                .orElseThrow(() -> new IllegalStateException("Broker credentials are not configured for client " + brokerClientId));
    }

    private BrokerTradingAdapter adapterFor(DecryptedBrokerCredentials credentials) {
        return adapters.stream()
                .filter(adapter -> adapter.brokerCode() == credentials.brokerCode())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Broker adapter is not configured for " + credentials.brokerCode()));
    }
}
