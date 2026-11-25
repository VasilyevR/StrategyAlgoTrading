package rv.trading.strategy.engine.web.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import rv.trading.strategy.contracts.dto.internal.OrderView;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.engine.TestFixtures;
import rv.trading.strategy.engine.service.OrderService;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.OrderMapperImpl;
import rv.trading.strategy.engine.security.InternalTokenAuthFilter;
import rv.trading.strategy.engine.web.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalOrderController.class, properties = "services.internal-token=test-internal-token")
@AutoConfigureMockMvc(addFilters = true)
@Import({InternalTokenAuthFilter.class, GlobalExceptionHandler.class})
class InternalOrderControllerTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void serializesOrderViewWithEnumDecimalAndInstantValues() throws Exception {
        OrderView view = new OrderMapperImpl().toView(TestFixtures.order(17L));
        given(orderService.getOrder(17L)).willReturn(view);

        mockMvc.perform(get("/internal/orders/17").header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.orderType").value("LIMIT"))
                .andExpect(jsonPath("$.timeInForce").value("GTC"))
                .andExpect(jsonPath("$.status").value("PARTIALLY_FILLED"))
                .andExpect(jsonPath("$.quantity").value(2.5))
                .andExpect(jsonPath("$.createdAt").value("2025-01-01T10:00:00Z"));
    }

    @Test
    void serializesPageResponseAndPaginationMetadata() throws Exception {
        OrderView view = new OrderMapperImpl().toView(TestFixtures.order(17L));
        given(orderService.getOrders(List.of(101L), 1, 2))
                .willReturn(new PageResponse<>(List.of(view), 1, 2, 5, 3));

        mockMvc.perform(get("/internal/orders")
                        .param("brokerClientIds", "101")
                        .param("page", "1")
                        .param("size", "2")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(17))
                .andExpect(jsonPath("$.content[0].side").value("BUY"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid"})
    void rejectsRequestsWithoutValidInternalToken(String tokenState) throws Exception {
        var request = get("/internal/orders/17");
        if ("invalid".equals(tokenState)) {
            request.header("X-Internal-Token", "wrong-token");
        }

        mockMvc.perform(request).andExpect(status().isUnauthorized());
        verifyNoInteractions(orderService);
    }

    @Test
    void mapsMissingOrderToProblemDetailNotFound() throws Exception {
        given(orderService.getOrder(77L)).willThrow(new ResourceNotFoundException("Order", 77L));

        mockMvc.perform(get("/internal/orders/77").header("X-Internal-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveOrderIdWithProblemDetail(String id) throws Exception {
        mockMvc.perform(get("/internal/orders/{id}", id).header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void rejectsEmptyBrokerClientIds() throws Exception {
        mockMvc.perform(get("/internal/orders").param("brokerClientIds", "").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonPositiveBrokerClientId() throws Exception {
        mockMvc.perform(get("/internal/orders").param("brokerClientIds", "-1").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "201"})
    void rejectsOutOfRangePageSize(String size) throws Exception {
        mockMvc.perform(get("/internal/orders")
                        .param("brokerClientIds", "101")
                        .param("size", size)
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/internal/orders")
                        .param("brokerClientIds", "101")
                        .param("page", "-1")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
