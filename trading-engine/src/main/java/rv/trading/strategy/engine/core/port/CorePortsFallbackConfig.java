package rv.trading.strategy.engine.core.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.AlgotradingStatus;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Configuration
public class CorePortsFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(RuntimeConfigPort.class)
    public RuntimeConfigPort runtimeConfigPort() {
        return new RuntimeConfigPort() {
            @Override
            public List<AlgotradingRuntimeConfig> loadActiveConfigs() {
                return List.of();
            }

            @Override
            public Optional<AlgotradingRuntimeConfig> loadConfig(long algotradingId) {
                return Optional.empty();
            }

            @Override
            public void reportWorkerStatus(long algotradingId, AlgotradingStatus status, String errorMessage) {
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(MarketHistoryPort.class)
    public MarketHistoryPort marketHistoryPort() {
        return new MarketHistoryPort() {
            @Override
            public List<CandleEvent> loadRecentCandles(long instrumentPairId, TimeframeCode timeframeCode,
                                                       int limit) {
                return List.of();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(BrokerExecutionPort.class)
    public BrokerExecutionPort brokerExecutionPort() {
        return new BrokerExecutionPort() {
            @Override
            public BigDecimal getAvailableBalance(long brokerClientId, long instrumentPairId) {
                throw unsupported();
            }

            @Override
            public OrderExecutionReceipt placeOrder(PlaceOrderCommand command) {
                throw unsupported();
            }

            @Override
            public void amendStopLoss(AmendStopLossCommand command) {
                throw unsupported();
            }

            @Override
            public void cancelOrder(long brokerClientId, long instrumentPairId, String exchangeOrderId) {
                throw unsupported();
            }

            private UnsupportedOperationException unsupported() {
                return new UnsupportedOperationException("BrokerExecutionPort adapter is not configured");
            }
        };
    }
}
