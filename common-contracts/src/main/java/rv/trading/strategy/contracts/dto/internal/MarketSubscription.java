package rv.trading.strategy.contracts.dto.internal;

import rv.trading.strategy.contracts.enums.BrokerCode;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.util.List;

public record MarketSubscription(
        long instrumentPairId,
        BrokerCode brokerCode,
        String symbol,
        String marketCategory,
        List<TimeframeCode> timeframes
) {
}
