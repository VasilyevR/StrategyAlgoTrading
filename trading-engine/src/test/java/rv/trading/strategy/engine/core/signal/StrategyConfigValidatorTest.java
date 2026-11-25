package rv.trading.strategy.engine.core.signal;

import org.junit.jupiter.api.Test;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.TrailingStopIndicator;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrategyConfigValidatorTest {

    private static final String SYMBOL = "BTCUSD";
    private static final String ALIAS_ENTRY = "entry";
    private static final String ALIAS_STOP = "stop";
    private static final String BLANK_ALIAS = "";

    private final TestIndicatorEvaluator entryEvaluator = new TestIndicatorEvaluator(IndicatorCode.SMA_CROSS);
    private final TestIndicatorEvaluator trendEvaluator = new TestIndicatorEvaluator(IndicatorCode.MACD);
    private final TrailingTestIndicatorEvaluator trailingEvaluator =
            new TrailingTestIndicatorEvaluator(IndicatorCode.SAR);
    private final IndicatorRegistry registry = new IndicatorRegistry(
            List.of(entryEvaluator, trendEvaluator, trailingEvaluator));
    private final StrategyConfigValidator validator = new StrategyConfigValidator(registry);

    @Test
    void acceptsValidRuntimeConfigWithRequiredTrailingStop() {
        AlgotradingRuntimeConfig config = runtimeConfig(ExitMode.HYBRID, List.of(
                indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER),
                indicator(IndicatorCode.SAR, ALIAS_STOP, IndicatorRole.TRAILING_STOP)
        ));

        assertThatCode(() -> validator.validate(config)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrInvalidTopLevelRuntimeFields() {
        assertInvalidConfig(null);
        assertInvalidConfig(runtimeConfig(0, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 0, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 0, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, BLANK_ALIAS, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, null, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, null,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                null, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, null, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, null,
                BigDecimal.ONE, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                null, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
        assertInvalidConfig(runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH,
                ExitMode.SIGNAL_MARKET, SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
    }

    @Test
    void rejectsEmptyNullMalformedAndDuplicateIndicatorConfigs() {
        assertInvalidIndicators(null);
        assertInvalidIndicators(List.of());
        assertInvalidIndicators(Collections.singletonList(null));
        assertInvalidIndicators(List.of(new StrategyIndicatorConfig(null, ALIAS_ENTRY, IndicatorRole.TRIGGER,
                1, Map.of())));
        assertInvalidIndicators(List.of(new StrategyIndicatorConfig(IndicatorCode.SMA_CROSS, null,
                IndicatorRole.TRIGGER, 1, Map.of())));
        assertInvalidIndicators(List.of(indicator(IndicatorCode.SMA_CROSS, BLANK_ALIAS, IndicatorRole.TRIGGER)));
        assertInvalidIndicators(List.of(new StrategyIndicatorConfig(IndicatorCode.SMA_CROSS, ALIAS_ENTRY,
                null, 1, Map.of())));
        assertInvalidIndicators(List.of(new StrategyIndicatorConfig(IndicatorCode.SMA_CROSS, ALIAS_ENTRY,
                IndicatorRole.TRIGGER, 0, Map.of())));
        assertInvalidIndicators(List.of(new StrategyIndicatorConfig(IndicatorCode.SMA_CROSS, ALIAS_ENTRY,
                IndicatorRole.TRIGGER, 1, null)));
        assertInvalidIndicators(List.of(
                indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER),
                indicator(IndicatorCode.MACD, ALIAS_ENTRY, IndicatorRole.FILTER)));
        assertInvalidIndicators(List.of(indicator(IndicatorCode.CMF, ALIAS_ENTRY, IndicatorRole.TRIGGER)));
    }

    @Test
    void requiresDirectionalIndicatorAndTrailingStopForTrailingExitModes() {
        assertInvalidIndicators(List.of(indicator(IndicatorCode.SAR, ALIAS_STOP, IndicatorRole.TRAILING_STOP)));

        for (ExitMode exitMode : List.of(ExitMode.TRAILING_STOP, ExitMode.HYBRID)) {
            assertInvalidConfig(runtimeConfig(exitMode, List.of(
                    indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER))));
            assertInvalidConfig(runtimeConfig(exitMode, List.of(
                    indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER),
                    indicator(IndicatorCode.SMA_CROSS, ALIAS_STOP, IndicatorRole.TRAILING_STOP))));
        }
    }

    @Test
    void delegatesParameterValidationToResolvedEvaluator() {
        validator.validateIndicators(List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_ENTRY, IndicatorRole.TRIGGER)));

        assertThat(entryEvaluator.validationCount).isEqualTo(1);
    }

    private void assertInvalidConfig(AlgotradingRuntimeConfig config) {
        assertThatThrownBy(() -> validator.validate(config)).isInstanceOf(IndicatorConfigurationException.class);
    }

    private void assertInvalidIndicators(List<StrategyIndicatorConfig> indicators) {
        assertThatThrownBy(() -> validator.validateIndicators(indicators))
                .isInstanceOf(IndicatorConfigurationException.class);
    }

    private static StrategyIndicatorConfig indicator(IndicatorCode code, String alias, IndicatorRole role) {
        return new StrategyIndicatorConfig(code, alias, role, 1, Map.of());
    }

    private static AlgotradingRuntimeConfig runtimeConfig(ExitMode exitMode,
                                                          List<StrategyIndicatorConfig> indicators) {
        return runtimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH, exitMode,
                SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, indicators);
    }

    private static AlgotradingRuntimeConfig runtimeConfig(long algotradingId, long brokerClientId,
                                                          long instrumentPairId, String symbol,
                                                          TimeframeCode timeframeCode, DirectionMode directionMode,
                                                          ExitMode exitMode, SizingMode sizingMode,
                                                          BigDecimal orderVolume, BigDecimal tickSize,
                                                          BigDecimal stepSize,
                                                          List<StrategyIndicatorConfig> indicators) {
        return new AlgotradingRuntimeConfig(algotradingId, brokerClientId, instrumentPairId, symbol, timeframeCode,
                directionMode, exitMode, sizingMode, orderVolume, tickSize, stepSize, indicators);
    }

    private static class TestIndicatorEvaluator implements IndicatorEvaluator {

        private final IndicatorCode code;
        private int validationCount;

        private TestIndicatorEvaluator(IndicatorCode code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code.name();
        }

        @Override
        public void validateParams(Map<String, Object> params) {
            validationCount++;
        }

        @Override
        public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role,
                                                     Map<String, Object> params) {
            return new IndicatorEvaluation(new BitSet(), Map.of());
        }
    }

    private static final class TrailingTestIndicatorEvaluator extends TestIndicatorEvaluator
            implements TrailingStopIndicator {

        private TrailingTestIndicatorEvaluator(IndicatorCode code) {
            super(code);
        }

        @Override
        public Optional<BigDecimal> calculateStopLoss(BarSeries series, PositionSide side,
                                                      Map<String, Object> params) {
            return Optional.empty();
        }
    }
}
