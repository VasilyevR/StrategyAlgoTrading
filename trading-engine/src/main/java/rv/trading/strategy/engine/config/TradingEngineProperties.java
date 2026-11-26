package rv.trading.strategy.engine.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.engine.broker.BrokerClientEntry;

import java.util.ArrayList;
import java.util.List;

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

    public String getBybitLiveBaseUrl() {
        return bybitLiveBaseUrl;
    }

    public void setBybitLiveBaseUrl(String bybitLiveBaseUrl) {
        this.bybitLiveBaseUrl = bybitLiveBaseUrl;
    }

    public String getBybitDemoBaseUrl() {
        return bybitDemoBaseUrl;
    }

    public void setBybitDemoBaseUrl(String bybitDemoBaseUrl) {
        this.bybitDemoBaseUrl = bybitDemoBaseUrl;
    }

    public long getBybitRecvWindowMillis() {
        return bybitRecvWindowMillis;
    }

    public void setBybitRecvWindowMillis(long bybitRecvWindowMillis) {
        this.bybitRecvWindowMillis = bybitRecvWindowMillis;
    }

    public String getMasterEncryptionKey() {
        return masterEncryptionKey;
    }

    public void setMasterEncryptionKey(String masterEncryptionKey) {
        this.masterEncryptionKey = masterEncryptionKey;
    }

    public List<BrokerClientEntry> getBrokerClients() {
        return brokerClients;
    }

    public void setBrokerClients(List<BrokerClientEntry> brokerClients) {
        this.brokerClients = brokerClients;
    }

    public List<AlgotradingRuntimeConfig> getAlgotradings() {
        return algotradings;
    }

    public void setAlgotradings(List<AlgotradingRuntimeConfig> algotradings) {
        this.algotradings = algotradings;
    }
}
