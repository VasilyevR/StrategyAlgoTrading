package rv.trading.strategy.engine.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class InternalHttpClientsConfig {

    @Bean
    JdkClientHttpRequestFactory internalHttpRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return requestFactory;
    }

    @Bean("portalApiRestClient")
    RestClient portalApiRestClient(
            @Value("${services.portal-api.url}") String baseUrl,
            @Value("${services.internal-token}") String internalToken,
            JdkClientHttpRequestFactory requestFactory
    ) {
        requireInternalToken(internalToken);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    @Bean("marketCollectorRestClient")
    RestClient marketCollectorRestClient(
            @Value("${services.market-collector.url}") String baseUrl,
            @Value("${services.internal-token}") String internalToken,
            JdkClientHttpRequestFactory requestFactory
    ) {
        requireInternalToken(internalToken);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }

    @Bean("portalApiHttpServiceProxyFactory")
    HttpServiceProxyFactory portalApiHttpServiceProxyFactory(
            @Qualifier("portalApiRestClient") RestClient restClient
    ) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    @Bean("marketCollectorHttpServiceProxyFactory")
    HttpServiceProxyFactory marketCollectorHttpServiceProxyFactory(
            @Qualifier("marketCollectorRestClient") RestClient restClient
    ) {
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    private static void requireInternalToken(String internalToken) {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("services.internal-token must not be blank");
        }
    }
}
