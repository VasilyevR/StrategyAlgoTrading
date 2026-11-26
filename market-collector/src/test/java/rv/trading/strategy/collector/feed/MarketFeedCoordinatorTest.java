package rv.trading.strategy.collector.feed;

import org.junit.jupiter.api.Test;
import org.instancio.Instancio;
import org.instancio.Select;
import rv.trading.strategy.contracts.dto.internal.MarketSubscription;
import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketFeedCoordinatorTest {

    @Test
    void groupsSubscriptionsByBrokerAndSkipsMissingFeedImplementations() {
        MarketSubscription bybitSubscription = subscription(1L, BrokerCode.BYBIT);
        MarketSubscription ibkrSubscription = subscription(2L, BrokerCode.IBKR);
        MarketSubscriptionPort subscriptionPort = mock(MarketSubscriptionPort.class);
        ExchangeMarketDataFeed bybitFeed = mock(ExchangeMarketDataFeed.class);
        when(subscriptionPort.loadSubscriptions()).thenReturn(List.of(bybitSubscription, ibkrSubscription));
        when(bybitFeed.brokerCode()).thenReturn(BrokerCode.BYBIT);
        MarketFeedCoordinator coordinator = new MarketFeedCoordinator(subscriptionPort, List.of(bybitFeed));

        coordinator.start();

        assertThat(coordinator.isRunning()).isTrue();
        verify(bybitFeed, timeout(TimeUnit.SECONDS.toMillis(2)))
                .start(List.of(bybitSubscription));
        verify(subscriptionPort).loadSubscriptions();
        verify(bybitFeed, never()).start(List.of(ibkrSubscription));

        coordinator.stop();

        verify(bybitFeed).stop();
        assertThat(coordinator.isRunning()).isFalse();
    }

    private static MarketSubscription subscription(long instrumentPairId, BrokerCode brokerCode) {
        return Instancio.of(MarketSubscription.class)
                .set(Select.field(MarketSubscription.class, "instrumentPairId"), instrumentPairId)
                .set(Select.field(MarketSubscription.class, "brokerCode"), brokerCode)
                .set(Select.field(MarketSubscription.class, "symbol"), "BTCUSDT")
                .set(Select.field(MarketSubscription.class, "marketCategory"), "linear")
                .set(Select.field(MarketSubscription.class, "timeframes"), List.of(TimeframeCode.M1))
                .create();
    }
}
