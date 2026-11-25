package rv.trading.strategy.engine.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;
import rv.trading.strategy.engine.security.InternalTokenAuthFilter;
import rv.trading.strategy.engine.service.SignalService;
import rv.trading.strategy.engine.web.internal.InternalSignalController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalSignalController.class, properties = "services.internal-token=test-internal-token")
@AutoConfigureMockMvc(addFilters = true)
@Import({InternalTokenAuthFilter.class, GlobalExceptionHandler.class})
class IndicatorConfigurationExceptionHandlerTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignalService signalService;

    @Test
    void mapsConfigurationExceptionToBadRequestProblemDetail() throws Exception {
        org.mockito.BDDMockito.given(signalService.getSignal(98L))
                .willThrow(new IndicatorConfigurationException(IndicatorCode.MACD, "Invalid MACD parameters"));

        mockMvc.perform(get("/internal/signals/98").header("X-Internal-Token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid indicator configuration"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.indicatorCode").value(IndicatorCode.MACD.name()));
    }

}
