package rv.trading.strategy.engine.broker;

import rv.trading.strategy.contracts.enums.BrokerCode;

public record BrokerClientEntry(
        long brokerClientId,
        BrokerCode brokerCode,
        String apiKey,
        String apiSecret,
        boolean encrypted,
        boolean paper,
        String marketCategory
) {
}
