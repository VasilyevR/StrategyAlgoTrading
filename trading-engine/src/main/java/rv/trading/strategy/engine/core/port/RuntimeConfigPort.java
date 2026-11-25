package rv.trading.strategy.engine.core.port;

import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.AlgotradingStatus;

import java.util.List;
import java.util.Optional;

public interface RuntimeConfigPort {
    List<AlgotradingRuntimeConfig> loadActiveConfigs();

    Optional<AlgotradingRuntimeConfig> loadConfig(long algotradingId);

    void reportWorkerStatus(long algotradingId, AlgotradingStatus status, String errorMessage);
}
