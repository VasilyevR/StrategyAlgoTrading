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
import rv.trading.strategy.contracts.dto.internal.PositionView;
import rv.trading.strategy.engine.TestFixtures;
import rv.trading.strategy.engine.service.PositionService;
import rv.trading.strategy.engine.service.exception.ResourceNotFoundException;
import rv.trading.strategy.engine.service.mapper.PositionMapperImpl;
import rv.trading.strategy.engine.security.InternalTokenAuthFilter;
import rv.trading.strategy.engine.web.GlobalExceptionHandler;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalPositionController.class, properties = "services.internal-token=test-internal-token")
@AutoConfigureMockMvc(addFilters = true)
@Import({InternalTokenAuthFilter.class, GlobalExceptionHandler.class})
class InternalPositionControllerTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PositionService positionService;

    @Test
    void serializesPositionViewWithEnumDecimalAndInstantValues() throws Exception {
        PositionView view = new PositionMapperImpl().toView(TestFixtures.position(27L));
        given(positionService.getPosition(27L)).willReturn(view);

        mockMvc.perform(get("/internal/positions/27").header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(27))
                .andExpect(jsonPath("$.side").value("LONG"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.quantity").value(2.5))
                .andExpect(jsonPath("$.avgEntryPrice").value(100.25))
                .andExpect(jsonPath("$.openedAt").value("2025-01-01T10:00:00Z"));
    }

    @Test
    void mapsMissingPositionToProblemDetailNotFound() throws Exception {
        given(positionService.getPosition(88L)).willThrow(new ResourceNotFoundException("Position", 88L));

        mockMvc.perform(get("/internal/positions/88").header("X-Internal-Token", TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "invalid"})
    void rejectsRequestsWithoutValidInternalToken(String tokenState) throws Exception {
        var request = get("/internal/positions/27");
        if ("invalid".equals(tokenState)) {
            request.header("X-Internal-Token", "wrong-token");
        }

        mockMvc.perform(request).andExpect(status().isUnauthorized());
        verifyNoInteractions(positionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositivePositionId(String id) throws Exception {
        mockMvc.perform(get("/internal/positions/{id}", id).header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void serializesPositionCollectionAndOptionalStatusFilter() throws Exception {
        PositionView view = new PositionMapperImpl().toView(TestFixtures.position(27L));
        given(positionService.getPositions(List.of(101L), view.status())).willReturn(List.of(view));

        mockMvc.perform(get("/internal/positions")
                        .param("brokerClientIds", "101")
                        .param("status", "OPEN")
                        .header("X-Internal-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(27))
                .andExpect(jsonPath("$[0].side").value("LONG"))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void rejectsEmptyBrokerClientIds() throws Exception {
        mockMvc.perform(get("/internal/positions").param("brokerClientIds", "").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNonPositiveBrokerClientId() throws Exception {
        mockMvc.perform(get("/internal/positions").param("brokerClientIds", "-1").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
