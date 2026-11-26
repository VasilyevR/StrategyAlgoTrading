package rv.trading.strategy.collector.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rv.trading.strategy.collector.feed.MarketSubscriptionPort;
import rv.trading.strategy.collector.feed.PropertiesMarketSubscriptionPort;

import java.net.http.HttpClient;

@Configuration
public class MarketCollectorPortsConfig {

    @Bean
    @ConditionalOnMissingBean(MarketSubscriptionPort.class)
    public MarketSubscriptionPort marketSubscriptionPort(MarketCollectorProperties properties) {
        return new PropertiesMarketSubscriptionPort(properties);
    }

    @Bean
    public HttpClient marketDataHttpClient() {
        return HttpClient.newBuilder().build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
