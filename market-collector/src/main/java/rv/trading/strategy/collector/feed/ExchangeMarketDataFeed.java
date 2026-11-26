package rv.trading.strategy.collector.feed;

import rv.trading.strategy.contracts.dto.internal.MarketSubscription;
import rv.trading.strategy.contracts.enums.BrokerCode;

import java.util.List;

public interface ExchangeMarketDataFeed {
    BrokerCode brokerCode();

    void start(List<MarketSubscription> subscriptions);

    void stop();
}
