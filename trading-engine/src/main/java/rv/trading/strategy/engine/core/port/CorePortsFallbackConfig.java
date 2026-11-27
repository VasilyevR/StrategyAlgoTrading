package rv.trading.strategy.engine.core.port;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import rv.trading.strategy.contracts.crypto.AesGcmCipher;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.AlgotradingStatus;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.engine.broker.BrokerClientEntry;
import rv.trading.strategy.engine.broker.BrokerCredentialsPort;
import rv.trading.strategy.engine.broker.DecryptedBrokerCredentials;
import rv.trading.strategy.engine.config.TradingEngineProperties;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.List;
import java.util.Optional;

@Configuration
public class CorePortsFallbackConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(CorePortsFallbackConfig.class);
    private static final String UPDATE_ALGOTRADING_STATUS_SQL = """
            UPDATE algotrading
            SET status = :status,
                error_message = :errorMessage,
                updated_at = NOW()
            WHERE id = :id
            """;

    @Bean
    @ConditionalOnMissingBean(BrokerCredentialsPort.class)
    public BrokerCredentialsPort brokerCredentialsPort(TradingEngineProperties properties) {
        return brokerClientId -> properties.getBrokerClients().stream()
                .filter(entry -> entry.brokerClientId() == brokerClientId)
                .findFirst()
                .map(entry -> decryptCredentials(entry, properties.getMasterEncryptionKey()));
    }

    private static DecryptedBrokerCredentials decryptCredentials(BrokerClientEntry entry, String masterKey) {
        String apiKey = entry.apiKey();
        String apiSecret = entry.apiSecret();
        if (entry.encrypted()) {
            byte[] key = AesGcmCipher.decodeBase64Key(masterKey);
            apiKey = AesGcmCipher.decrypt(apiKey, key);
            apiSecret = AesGcmCipher.decrypt(apiSecret, key);
        }
        return new DecryptedBrokerCredentials(entry.brokerClientId(), entry.brokerCode(), apiKey,
                apiSecret, entry.paper(), entry.marketCategory());
    }

    @Bean
    @ConditionalOnMissingBean(RuntimeConfigPort.class)
    public RuntimeConfigPort runtimeConfigPort(TradingEngineProperties properties,
                                               ObjectProvider<JdbcClient> jdbcClientProvider) {
        return new RuntimeConfigPort() {
            @Override
            public List<AlgotradingRuntimeConfig> loadActiveConfigs() {
                return List.copyOf(properties.getAlgotradings());
            }

            @Override
            public Optional<AlgotradingRuntimeConfig> loadConfig(long algotradingId) {
                return properties.getAlgotradings().stream()
                        .filter(config -> config.algotradingId() == algotradingId)
                        .findFirst();
            }

            @Override
            public void reportWorkerStatus(long algotradingId, AlgotradingStatus status, String errorMessage) {
                LOGGER.info("Worker status updated: algotradingId={}, status={}, error={}",
                        algotradingId, status, errorMessage);
                JdbcClient jdbcClient = jdbcClientProvider.getIfAvailable();
                if (jdbcClient == null || status == null) {
                    return;
                }
                try {
                    jdbcClient.sql(UPDATE_ALGOTRADING_STATUS_SQL)
                            .param("status", status.name())
                            .param("errorMessage", errorMessage, Types.VARCHAR)
                            .param("id", algotradingId)
                            .update();
                } catch (RuntimeException exception) {
                    LOGGER.warn("Failed to persist worker status in database for algotradingId={}: {}",
                            algotradingId, exception.getMessage());
                }
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
