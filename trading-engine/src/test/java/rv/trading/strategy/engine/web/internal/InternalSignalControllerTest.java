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
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.contracts.dto.internal.SignalView;
import rv.trading.strategy.engine.TestFixtures;
import rv.trading.strategy.engine.service.SignalService;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.SignalMapperImpl;
import rv.trading.strategy.engine.security.InternalTokenAuthFilter;
import rv.trading.strategy.engine.web.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalSignalController.class, properties = "services.internal-token=test-internal-token")
@AutoConfigureMockMvc(addFilters = true)
@Import({InternalTokenAuthFilter.class, GlobalExceptionHandler.class})
class InternalSignalControllerTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignalService signalService;

    @Test
    void serializesSignalViewWithEnumDecimalInstantAndSnapshotValues() throws Exception {
        SignalView view = new SignalMapperImpl().toView(TestFixtures.signal(37L));
        given(signalService.getSignal(37L)).willReturn(view);

        mockMvc.perform(get("/internal/signals/37").header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(37))
                .andExpect(jsonPath("$.signalType").value("BUY_OPEN"))
                .andExpect(jsonPath("$.status").value("DETECTED"))
                .andExpect(jsonPath("$.triggerPrice").value(100.25))
                .andExpect(jsonPath("$.indicatorSnapshot.trend").value("up"))
                .andExpect(jsonPath("$.candleOpenTime").value("2025-01-01T10:00:00Z"));
    }

    @Test
    void serializesSignalPageResponseAndPaginationMetadata() throws Exception {
        SignalView view = new SignalMapperImpl().toView(TestFixtures.signal(37L));
        given(signalService.getSignals(301L, 1, 5))
                .willReturn(new PageResponse<>(List.of(view), 1, 5, 6, 2));

        mockMvc.perform(get("/internal/signals")
                        .param("algotradingId", "301")
                        .param("page", "1")
                        .param("size", "5")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].signalType").value("BUY_OPEN"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void mapsMissingSignalToProblemDetailNotFound() throws Exception {
        given(signalService.getSignal(98L)).willThrow(new ResourceNotFoundException("Signal", 98L));

        mockMvc.perform(get("/internal/signals/98").header("X-Internal-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid"})
    void rejectsRequestsWithoutValidInternalToken(String tokenState) throws Exception {
        var request = get("/internal/signals/37");
        if ("invalid".equals(tokenState)) {
            request.header("X-Internal-Token", "wrong-token");
        }

        mockMvc.perform(request).andExpect(status().isUnauthorized());
        verifyNoInteractions(signalService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveSignalId(String id) throws Exception {
        mockMvc.perform(get("/internal/signals/{id}", id).header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsNonPositiveAlgotradingId() throws Exception {
        mockMvc.perform(get("/internal/signals")
                        .param("algotradingId", "0")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "201"})
    void rejectsOutOfRangePageSize(String size) throws Exception {
        mockMvc.perform(get("/internal/signals")
                        .param("algotradingId", "301")
                        .param("size", size)
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/internal/signals")
                        .param("algotradingId", "301")
                        .param("page", "-1")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
