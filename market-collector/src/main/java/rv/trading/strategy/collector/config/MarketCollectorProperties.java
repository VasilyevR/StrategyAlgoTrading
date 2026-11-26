package rv.trading.strategy.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import rv.trading.strategy.contracts.dto.internal.MarketSubscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Component
@Validated
@ConfigurationProperties(prefix = "market-collector")
public class MarketCollectorProperties {

    @NotBlank
    private String bybitRestBaseUrl;

    @NotBlank
    private String bybitWebSocketBaseUrl;

    @Positive
    private int backfillLimit;

    @Positive
    private int heartbeatIntervalSeconds;

    @Positive
    private long reconnectInitialDelayMillis;

    @Positive
    private long reconnectMaxDelayMillis;

    private List<MarketSubscription> subscriptions = new ArrayList<>();
}
