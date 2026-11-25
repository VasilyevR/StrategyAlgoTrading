package rv.trading.strategy.engine.core.signal;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.engine.core.indicator.DirectionBitMask;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorTestSeries;

import java.math.BigDecimal;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SignalBitMaskEvaluatorTest {

    private static final String SYMBOL = "BTCUSD";
    private static final String ALIAS_TREND = "trend";
    private static final String ALIAS_CONFIRMATION = "confirmation";
    private static final String ALIAS_UP = "up";
    private static final String ALIAS_DOWN = "down";
    private static final String ALIAS_MOMENTUM = "momentum";
    private static final String ALIAS_STOP = "stop";

    private final StubIndicatorEvaluator upEvaluator = new StubIndicatorEvaluator(
            IndicatorCode.SMA_CROSS, DirectionBitMask.of(true, false), Map.of(IndicatorSnapshotKeys.CLOSE, 101));
    private final StubIndicatorEvaluator downEvaluator = new StubIndicatorEvaluator(
            IndicatorCode.MACD, DirectionBitMask.of(false, true), Map.of(IndicatorSnapshotKeys.MACD, -1));
    private final StubIndicatorEvaluator bothEvaluator = new StubIndicatorEvaluator(
            IndicatorCode.CMF, DirectionBitMask.of(true, true), Map.of(IndicatorSnapshotKeys.CMF, 0.5));
    private final StubIndicatorEvaluator trailingEvaluator = new StubIndicatorEvaluator(
            IndicatorCode.SAR, DirectionBitMask.of(false, true), Map.of(IndicatorSnapshotKeys.SAR, 99));
    private final SignalBitMaskEvaluator evaluator = new SignalBitMaskEvaluator(new IndicatorRegistry(
            List.of(upEvaluator, downEvaluator, bothEvaluator, trailingEvaluator)));
    private final BarSeries series = IndicatorTestSeries.rising(2);

    @Test
    void intersectsIndicatorsInEachGroupAndClearsConflictingOrGroups() {
        AlgotradingRuntimeConfig intersectedConfig = config(List.of(
                indicator(IndicatorCode.SMA_CROSS, ALIAS_TREND, IndicatorRole.TRIGGER, 1),
                indicator(IndicatorCode.CMF, ALIAS_CONFIRMATION, IndicatorRole.FILTER, 1)
        ), ExitMode.SIGNAL_MARKET);

        SignalEvaluationResult intersected = evaluator.evaluate(series, intersectedConfig, flatContext());

        assertThat(intersected.resolvedDirectionMask()).isEqualTo(DirectionBitMask.of(true, false));
        assertThat(intersected.actions()).containsExactly(SignalType.BUY_OPEN);
        assertThat(intersected.indicatorSnapshot())
                .containsEntry(ALIAS_TREND + SignalBitMaskEvaluator.SNAPSHOT_KEY_SEPARATOR + IndicatorSnapshotKeys.CLOSE, 101)
                .containsEntry(ALIAS_CONFIRMATION + SignalBitMaskEvaluator.SNAPSHOT_KEY_SEPARATOR + IndicatorSnapshotKeys.CMF, 0.5);

        AlgotradingRuntimeConfig conflictingConfig = config(List.of(
                indicator(IndicatorCode.SMA_CROSS, ALIAS_UP, IndicatorRole.TRIGGER, 1),
                indicator(IndicatorCode.MACD, ALIAS_DOWN, IndicatorRole.FILTER, 2)
        ), ExitMode.SIGNAL_MARKET);

        SignalEvaluationResult conflicting = evaluator.evaluate(series, conflictingConfig, flatContext());

        assertThat(conflicting.resolvedDirectionMask()).isEqualTo(DirectionBitMask.empty());
        assertThat(conflicting.actions()).isEmpty();
    }

    @Test
    void mapsOppositeSignalsToCloseAndOptionalReverseOpen() {
        AlgotradingRuntimeConfig config = config(List.of(
                indicator(IndicatorCode.MACD, ALIAS_MOMENTUM, IndicatorRole.TRIGGER, 1),
                indicator(IndicatorCode.SAR, ALIAS_STOP, IndicatorRole.TRAILING_STOP, 2)
        ), ExitMode.HYBRID);

        SignalEvaluationResult result = evaluator.evaluate(series, config,
                new SignalEvaluationContext(Optional.of(PositionSide.LONG), Optional.empty()));

        assertThat(result.actions()).containsExactly(SignalType.BUY_CLOSE, SignalType.SELL_OPEN);
        assertThat(result.indicatorSnapshot())
                .containsEntry(ALIAS_MOMENTUM + SignalBitMaskEvaluator.SNAPSHOT_KEY_SEPARATOR + IndicatorSnapshotKeys.MACD, -1)
                .doesNotContainKey(ALIAS_STOP + SignalBitMaskEvaluator.SNAPSHOT_KEY_SEPARATOR + IndicatorSnapshotKeys.SAR);
    }

    @Test
    void trailingStopModeDoesNotOpenReverseDirectionBeforePositionCloses() {
        AlgotradingRuntimeConfig trailingStopConfig = config(List.of(
                indicator(IndicatorCode.MACD, ALIAS_MOMENTUM, IndicatorRole.TRIGGER, 1)
        ), ExitMode.TRAILING_STOP);

        SignalEvaluationResult longResult = evaluator.evaluate(series, trailingStopConfig,
                new SignalEvaluationContext(Optional.of(PositionSide.LONG), Optional.empty()));
        SignalEvaluationResult shortResult = evaluator.evaluate(series,
                config(List.of(indicator(IndicatorCode.SMA_CROSS, ALIAS_TREND, IndicatorRole.TRIGGER, 1)),
                        ExitMode.TRAILING_STOP),
                new SignalEvaluationContext(Optional.of(PositionSide.SHORT), Optional.empty()));

        assertThat(longResult.actions()).isEmpty();
        assertThat(shortResult.actions()).isEmpty();
    }

    @Test
    void directionMemorySuppressesRepeatedEntryInTheSameDirection() {
        AlgotradingRuntimeConfig config = config(List.of(
                indicator(IndicatorCode.SMA_CROSS, ALIAS_TREND, IndicatorRole.TRIGGER, 1)
        ), ExitMode.SIGNAL_MARKET);

        SignalEvaluationResult result = evaluator.evaluate(series, config,
                new SignalEvaluationContext(Optional.empty(), Optional.of(OrderSide.BUY)));

        assertThat(result.actions()).isEmpty();
    }

    private static SignalEvaluationContext flatContext() {
        return new SignalEvaluationContext(Optional.empty(), Optional.empty());
    }

    private static StrategyIndicatorConfig indicator(IndicatorCode code, String alias, IndicatorRole role,
                                                     int conditionGroup) {
        return new StrategyIndicatorConfig(code, alias, role, conditionGroup, Map.of());
    }

    private static AlgotradingRuntimeConfig config(List<StrategyIndicatorConfig> indicators, ExitMode exitMode) {
        return new AlgotradingRuntimeConfig(1, 2, 3, SYMBOL, TimeframeCode.M1, DirectionMode.BOTH, exitMode,
                SizingMode.FIXED_VOLUME, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, indicators);
    }

    private static final class StubIndicatorEvaluator implements IndicatorEvaluator {

        private final IndicatorCode code;
        private final BitSet mask;
        private final Map<String, Object> snapshot;

        private StubIndicatorEvaluator(IndicatorCode code, BitSet mask, Map<String, Object> snapshot) {
            this.code = code;
            this.mask = mask;
            this.snapshot = snapshot;
        }

        @Override
        public String code() {
            return code.name();
        }

        @Override
        public void validateParams(Map<String, Object> params) {
        }

        @Override
        public IndicatorEvaluation evaluateIndicator(BarSeries series, IndicatorRole role,
                                                     Map<String, Object> params) {
            return new IndicatorEvaluation(mask, snapshot);
        }
    }
}
