package rv.trading.strategy.collector.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.instancio.Instancio;
import org.instancio.Select;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import rv.trading.strategy.collector.security.InternalTokenAuthFilter;
import rv.trading.strategy.collector.service.CandleStorageService;
import rv.trading.strategy.contracts.InternalApiHeaders;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InternalCandleController.class,
        properties = "services.internal-token=test-internal-token")
@AutoConfigureMockMvc(addFilters = true)
@Import({InternalTokenAuthFilter.class, InternalApiValidationExceptionHandler.class})
class InternalCandleControllerTest {

    private static final String TOKEN = "test-internal-token";
    private static final long INSTRUMENT_PAIR_ID = 101L;
    private static final TimeframeCode TIMEFRAME_CODE = TimeframeCode.M1;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CandleStorageService candleStorageService;

    @Test
    void returnsCandleEventsWhenInternalTokenIsValid() throws Exception {
        Instant openTime = Instant.parse("2025-01-01T10:00:00Z");
        CandleEvent candle = Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(CandleEvent.class, "symbol"), "BTCUSDT")
                .set(Select.field(CandleEvent.class, "timeframeCode"), TIMEFRAME_CODE)
                .set(Select.field(CandleEvent.class, "openTime"), openTime)
                .set(Select.field(CandleEvent.class, "closeTime"), openTime.plusSeconds(60))
                .set(Select.field(CandleEvent.class, "open"), new BigDecimal("100.25"))
                .set(Select.field(CandleEvent.class, "high"), new BigDecimal("101.50"))
                .set(Select.field(CandleEvent.class, "low"), new BigDecimal("99.75"))
                .set(Select.field(CandleEvent.class, "close"), new BigDecimal("100.75"))
                .set(Select.field(CandleEvent.class, "volume"), new BigDecimal("12.5"))
                .set(Select.field(CandleEvent.class, "closed"), true)
                .create();
        given(candleStorageService.loadRecentCandles(INSTRUMENT_PAIR_ID, TIMEFRAME_CODE,
                Integer.parseInt(InternalCandleController.DEFAULT_LIMIT))).willReturn(List.of(candle));

        mockMvc.perform(get(InternalCandleController.CANDLES_PATH)
                        .param(InternalCandleController.INSTRUMENT_PAIR_ID_PARAM,
                                Long.toString(INSTRUMENT_PAIR_ID))
                        .param(InternalCandleController.TIMEFRAME_CODE_PARAM, TIMEFRAME_CODE.name())
                        .header(InternalApiHeaders.INTERNAL_TOKEN, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instrumentPairId").value(INSTRUMENT_PAIR_ID))
                .andExpect(jsonPath("$[0].symbol").value(candle.symbol()))
                .andExpect(jsonPath("$[0].timeframeCode").value(TIMEFRAME_CODE.name()))
                .andExpect(jsonPath("$[0].openTime").value(openTime.toString()))
                .andExpect(jsonPath("$[0].closeTime").value(openTime.plusSeconds(60).toString()))
                .andExpect(jsonPath("$[0].open").value(100.25))
                .andExpect(jsonPath("$[0].closed").value(true));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsMissingOrIncorrectInternalToken(boolean missingToken) throws Exception {
        var request = get(InternalCandleController.CANDLES_PATH)
                .param(InternalCandleController.INSTRUMENT_PAIR_ID_PARAM, Long.toString(INSTRUMENT_PAIR_ID))
                .param(InternalCandleController.TIMEFRAME_CODE_PARAM, TIMEFRAME_CODE.name());
        if (!missingToken) {
            request.header(InternalApiHeaders.INTERNAL_TOKEN, "incorrect-token");
        }

        mockMvc.perform(request).andExpect(status().isUnauthorized());
        verifyNoInteractions(candleStorageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void rejectsNonPositiveInstrumentPairId(String instrumentPairId) throws Exception {
        mockMvc.perform(get(InternalCandleController.CANDLES_PATH)
                        .param(InternalCandleController.INSTRUMENT_PAIR_ID_PARAM, instrumentPairId)
                        .param(InternalCandleController.TIMEFRAME_CODE_PARAM, TIMEFRAME_CODE.name())
                        .header(InternalApiHeaders.INTERNAL_TOKEN, TOKEN))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(candleStorageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1001"})
    void rejectsLimitOutsideAllowedRange(String limit) throws Exception {
        mockMvc.perform(validRequest().param(InternalCandleController.LIMIT_PARAM, limit))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(candleStorageService);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return get(InternalCandleController.CANDLES_PATH)
                .param(InternalCandleController.INSTRUMENT_PAIR_ID_PARAM, Long.toString(INSTRUMENT_PAIR_ID))
                .param(InternalCandleController.TIMEFRAME_CODE_PARAM, TIMEFRAME_CODE.name())
                .header(InternalApiHeaders.INTERNAL_TOKEN, TOKEN);
    }
}
