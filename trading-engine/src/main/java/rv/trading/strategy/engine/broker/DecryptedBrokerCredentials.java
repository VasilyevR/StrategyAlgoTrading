package rv.trading.strategy.engine.broker;

import rv.trading.strategy.contracts.enums.BrokerCode;

public record DecryptedBrokerCredentials(
        long brokerClientId,
        BrokerCode brokerCode,
        String apiKey,
        String apiSecret,
        boolean paper,
        String marketCategory
) {
}
