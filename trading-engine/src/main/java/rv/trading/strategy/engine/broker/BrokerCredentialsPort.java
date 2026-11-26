package rv.trading.strategy.engine.broker;

import java.util.Optional;

public interface BrokerCredentialsPort {
    Optional<DecryptedBrokerCredentials> loadCredentials(long brokerClientId);
}
