package rv.trading.strategy.engine.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenAuthFilterTest {

    private static final String TOKEN = "test-internal-token";

    @Test
    void rejectsInternalPathWhenTokenIsMissing() throws ServletException, IOException {
        assertRejected("/internal", null);
    }

    @Test
    void rejectsInternalSubpathWhenTokenIsMissing() throws ServletException, IOException {
        assertRejected("/internal/orders/1", null);
    }

    @Test
    void rejectsInternalPathWhenTokenIsWrong() throws ServletException, IOException {
        assertRejected("/internal", "incorrect-token");
    }

    @Test
    void rejectsInternalSubpathWhenTokenIsWrong() throws ServletException, IOException {
        assertRejected("/internal/orders/1", "incorrect-token");
    }

    @Test
    void allowsInternalSubpathWhenTokenMatches() throws ServletException, IOException {
        MockHttpServletRequest request = request("/internal/orders/1", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalTokenAuthFilter(TOKEN).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void stripsContextPathWhenMatchingInternalRequestUri() throws ServletException, IOException {
        MockHttpServletRequest request = request("/gateway/internal/orders/1", TOKEN);
        request.setContextPath("/gateway");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalTokenAuthFilter(TOKEN).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void requiresTokenForInternalRequestUriAfterStrippingContextPath() throws ServletException, IOException {
        assertRejected("/gateway/internal/orders/1", null, "/gateway");
    }

    @Test
    void bypassesNonInternalPathWithoutToken() throws ServletException, IOException {
        MockHttpServletRequest request = request("/actuator/health", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalTokenAuthFilter(TOKEN).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void constructorRejectsNullToken() {
        assertThatThrownBy(() -> new InternalTokenAuthFilter(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsBlankToken() {
        assertThatThrownBy(() -> new InternalTokenAuthFilter("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRejected(String path, String token) throws ServletException, IOException {
        assertRejected(path, token, "");
    }

    private static void assertRejected(String path, String token, String contextPath)
            throws ServletException, IOException {
        MockHttpServletRequest request = request(path, token);
        request.setContextPath(contextPath);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new InternalTokenAuthFilter(TOKEN).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader("X-Internal-Token", token);
        }
        return request;
    }
}
