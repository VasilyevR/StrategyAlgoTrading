package rv.trading.strategy.collector.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import rv.trading.strategy.contracts.InternalApiHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH = "/internal";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final byte[] expectedTokenBytes;

    public InternalTokenAuthFilter(@Value("${services.internal-token}") String expectedToken) {
        this.expectedTokenBytes = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !path.equals(INTERNAL_PATH) && !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String suppliedToken = request.getHeader(InternalApiHeaders.INTERNAL_TOKEN);
        if (suppliedToken == null || !MessageDigest.isEqual(
                expectedTokenBytes,
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
