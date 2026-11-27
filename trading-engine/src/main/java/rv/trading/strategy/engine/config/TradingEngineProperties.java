package rv.trading.strategy.engine.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.engine.broker.BrokerClientEntry;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "trading-engine")
public class TradingEngineProperties {
    @NotBlank
    private String bybitLiveBaseUrl;
    @NotBlank
    private String bybitDemoBaseUrl;
    @Positive
    private long bybitRecvWindowMillis;
    private String masterEncryptionKey;
    private List<BrokerClientEntry> brokerClients = new ArrayList<>();
    private List<AlgotradingRuntimeConfig> algotradings = new ArrayList<>();

}
