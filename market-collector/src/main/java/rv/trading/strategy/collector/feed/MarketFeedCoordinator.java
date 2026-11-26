package rv.trading.strategy.collector.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.dto.internal.MarketSubscription;
import rv.trading.strategy.contracts.enums.BrokerCode;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MarketFeedCoordinator implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketFeedCoordinator.class);

    private final MarketSubscriptionPort subscriptionPort;
    private final Map<BrokerCode, ExchangeMarketDataFeed> feeds;
    private final ExecutorService executor;
    private volatile boolean running;

    public MarketFeedCoordinator(MarketSubscriptionPort subscriptionPort,
                                 List<ExchangeMarketDataFeed> marketDataFeeds) {
        this.subscriptionPort = subscriptionPort;
        this.feeds = new EnumMap<>(BrokerCode.class);
        for (ExchangeMarketDataFeed feed : marketDataFeeds) {
            feeds.put(feed.brokerCode(), feed);
        }
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void start() {
        running = true;
        EnumMap<BrokerCode, java.util.ArrayList<MarketSubscription>> grouped =
                new EnumMap<>(BrokerCode.class);
        for (MarketSubscription subscription : subscriptionPort.loadSubscriptions()) {
            grouped.computeIfAbsent(subscription.brokerCode(), brokerCode ->
                    new java.util.ArrayList<>()).add(subscription);
        }
        for (Map.Entry<BrokerCode, java.util.ArrayList<MarketSubscription>> entry : grouped.entrySet()) {
            ExchangeMarketDataFeed feed = feeds.get(entry.getKey());
            if (feed == null) {
                LOGGER.warn("Skipping market subscriptions for broker {} because no feed implementation is registered",
                        entry.getKey());
                continue;
            }
            executor.execute(() -> feed.start(List.copyOf(entry.getValue())));
        }
    }

    @Override
    public void stop() {
        running = false;
        for (ExchangeMarketDataFeed feed : feeds.values()) {
            feed.stop();
        }
        executor.close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
