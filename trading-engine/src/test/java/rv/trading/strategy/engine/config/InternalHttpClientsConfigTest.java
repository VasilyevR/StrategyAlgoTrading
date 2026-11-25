package rv.trading.strategy.engine.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

class InternalHttpClientsConfigTest {

    private static final String TOKEN = "shared-test-token";

    private final InternalHttpClientsConfig config = new InternalHttpClientsConfig();
    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
        wireMock.stubFor(get(urlEqualTo("/probe")).willReturn(aResponse().withStatus(200)));
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void bothClientsSendInternalTokenHeader() {
        JdkClientHttpRequestFactory requestFactory = config.internalHttpRequestFactory();
        String baseUrl = wireMock.baseUrl();
        RestClient portalClient = config.portalApiRestClient(baseUrl, TOKEN, requestFactory);
        RestClient marketClient = config.marketCollectorRestClient(baseUrl, TOKEN, requestFactory);

        portalClient.get().uri("/probe").retrieve().toBodilessEntity();
        marketClient.get().uri("/probe").retrieve().toBodilessEntity();

        wireMock.verify(2, getRequestedFor(urlEqualTo("/probe")).withHeader("X-Internal-Token", equalTo(TOKEN)));
    }

    @Test
    void portalClientRejectsBlankInternalToken() {
        assertThatThrownBy(() -> config.portalApiRestClient(wireMock.baseUrl(), " ", config.internalHttpRequestFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("services.internal-token");
    }

    @Test
    void marketClientRejectsBlankInternalToken() {
        assertThatThrownBy(() -> config.marketCollectorRestClient(wireMock.baseUrl(), "", config.internalHttpRequestFactory()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("services.internal-token");
    }
}
