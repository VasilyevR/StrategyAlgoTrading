package rv.trading.strategy.collector.feed;

import rv.trading.strategy.contracts.dto.internal.MarketSubscription;
import rv.trading.strategy.collector.config.MarketCollectorProperties;

import java.util.ArrayList;
import java.util.List;

public class PropertiesMarketSubscriptionPort implements MarketSubscriptionPort {

    private final MarketCollectorProperties properties;

    public PropertiesMarketSubscriptionPort(MarketCollectorProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<MarketSubscription> loadSubscriptions() {
        if (properties.getSubscriptions() == null) {
            return List.of();
        }
        return new ArrayList<>(properties.getSubscriptions());
    }
}
