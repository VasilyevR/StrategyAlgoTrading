package rv.trading.strategy.collector.feed;

import rv.trading.strategy.contracts.dto.internal.MarketSubscription;

import java.util.List;

public interface MarketSubscriptionPort {
    List<MarketSubscription> loadSubscriptions();
}
