package rv.trading.strategy.engine.core.indicator.evaluator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.engine.core.indicator.DirectionBitMask;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorSnapshotKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorTestSeries;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class IndicatorEvaluatorsTest {

    private static final List<EvaluatorCase> CASES = List.of(
            new EvaluatorCase(new SmaCrossIndicatorEvaluator(), Map.of(IndicatorParamKeys.FAST_PERIOD, 2,
                    IndicatorParamKeys.SLOW_PERIOD, 3), 3, List.of(IndicatorSnapshotKeys.FAST, IndicatorSnapshotKeys.SLOW)),
            new EvaluatorCase(new MacdIndicatorEvaluator(), Map.of(IndicatorParamKeys.FAST_PERIOD, 2,
                    IndicatorParamKeys.SLOW_PERIOD, 3, IndicatorParamKeys.SIGNAL_PERIOD, 2), 5,
                    List.of(IndicatorSnapshotKeys.MACD, IndicatorSnapshotKeys.SIGNAL, IndicatorSnapshotKeys.HISTOGRAM)),
            new EvaluatorCase(new CmfIndicatorEvaluator(), Map.of(IndicatorParamKeys.PERIOD, 3), 3,
                    List.of(IndicatorSnapshotKeys.CMF)),
            new EvaluatorCase(new SarIndicatorEvaluator(), Map.of(), 2,
                    List.of(IndicatorSnapshotKeys.SAR, IndicatorSnapshotKeys.CLOSE)),
            new EvaluatorCase(new AoIndicatorEvaluator(), Map.of(IndicatorParamKeys.FAST_PERIOD, 2,
                    IndicatorParamKeys.SLOW_PERIOD, 3), 3, List.of(IndicatorSnapshotKeys.AO)),
            new EvaluatorCase(new UltimateOscillatorIndicatorEvaluator(), Map.of(IndicatorParamKeys.PERIOD_1, 2,
                    IndicatorParamKeys.PERIOD_2, 3, IndicatorParamKeys.PERIOD_3, 4), 6,
                    List.of(IndicatorSnapshotKeys.ULTOSC)),
            new EvaluatorCase(new CciIndicatorEvaluator(), Map.of(IndicatorParamKeys.PERIOD, 3), 3,
                    List.of(IndicatorSnapshotKeys.CCI)),
            new EvaluatorCase(new RocIndicatorEvaluator(), Map.of(IndicatorParamKeys.PERIOD, 2), 3,
                    List.of(IndicatorSnapshotKeys.ROC)),
            new EvaluatorCase(new KamaIndicatorEvaluator(), Map.of(IndicatorParamKeys.ER_PERIOD, 2,
                    IndicatorParamKeys.FAST_PERIOD, 2, IndicatorParamKeys.SLOW_PERIOD, 3), 3,
                    List.of(IndicatorSnapshotKeys.CLOSE, IndicatorSnapshotKeys.KAMA)),
            new EvaluatorCase(new StochasticIndicatorEvaluator(), Map.of(IndicatorParamKeys.K_PERIOD, 3,
                    IndicatorParamKeys.D_PERIOD, 2), 4, List.of(IndicatorSnapshotKeys.K, IndicatorSnapshotKeys.D)),
            new EvaluatorCase(new StochRviIndicatorEvaluator(), Map.of(IndicatorParamKeys.LENGTH, 1), 7,
                    List.of(IndicatorSnapshotKeys.STOCH_RVI)));

    static Stream<EvaluatorCase> allEvaluators() {
        return CASES.stream();
    }

    @ParameterizedTest
    @MethodSource("allEvaluators")
    void insufficientHistoryReturnsEmptyMaskAndSnapshot(EvaluatorCase evaluatorCase) {
        IndicatorEvaluation result = evaluatorCase.evaluator.evaluate(
                IndicatorTestSeries.rising(1), IndicatorRole.FILTER, Map.of());

        assertThat(result.directionMask().isEmpty()).isTrue();
        assertThat(result.snapshot()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("allEvaluators")
    void trailingStopRoleReturnsAnEmptyMask(EvaluatorCase evaluatorCase) {
        IndicatorEvaluation result = evaluatorCase.evaluator.evaluate(
                IndicatorTestSeries.rising(2), IndicatorRole.TRAILING_STOP, Map.of());

        assertThat(result.directionMask().isEmpty()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("allEvaluators")
    void filtersEvaluateAtTheirMinimumBarsAndExposeSnapshotValues(EvaluatorCase evaluatorCase) {
        IndicatorEvaluation result = evaluatorCase.evaluator.evaluate(
                IndicatorTestSeries.rising(evaluatorCase.minimumBars), IndicatorRole.FILTER, evaluatorCase.params);

        assertThat(result.snapshot()).containsKeys(evaluatorCase.snapshotKeys.toArray(String[]::new));
        assertThat(result.snapshot().values()).allSatisfy(value -> assertThat(value).isInstanceOf(Number.class));
    }

    @ParameterizedTest
    @MethodSource("allEvaluators")
    void triggersEvaluateWithCurrentAndPreviousBarsAvailable(EvaluatorCase evaluatorCase) {
        IndicatorEvaluation result = evaluatorCase.evaluator.evaluate(
                IndicatorTestSeries.rising(evaluatorCase.minimumBars + 1), IndicatorRole.TRIGGER,
                evaluatorCase.params);

        assertThat(result.snapshot()).containsKeys(evaluatorCase.snapshotKeys.toArray(String[]::new));
    }

    @Test
    void trendFiltersProduceBullishAndBearishDirectionBits() {
        assertTrend(new SmaCrossIndicatorEvaluator(), Map.of(IndicatorParamKeys.FAST_PERIOD, 2,
                IndicatorParamKeys.SLOW_PERIOD, 3), true);
        IndicatorEvaluator macd = new MacdIndicatorEvaluator();
        Map<String, Object> macdParams = Map.of(IndicatorParamKeys.FAST_PERIOD, 2,
                IndicatorParamKeys.SLOW_PERIOD, 3, IndicatorParamKeys.SIGNAL_PERIOD, 2);
        assertDirection(macd.evaluate(IndicatorTestSeries.fromCloses(java.util.stream.IntStream.range(0, 60)
                .mapToDouble(index -> 100.0 + index * index).toArray()), IndicatorRole.FILTER, macdParams), true, false);
        assertDirection(macd.evaluate(IndicatorTestSeries.fromCloses(java.util.stream.IntStream.range(0, 60)
                .mapToDouble(index -> 4000.0 - index * index).toArray()), IndicatorRole.FILTER, macdParams), false, true);
        assertTrend(new SarIndicatorEvaluator(), Map.of(), true);
        assertTrend(new AoIndicatorEvaluator(), Map.of(IndicatorParamKeys.FAST_PERIOD, 2,
                IndicatorParamKeys.SLOW_PERIOD, 3), true);
        assertTrend(new KamaIndicatorEvaluator(), Map.of(IndicatorParamKeys.ER_PERIOD, 2,
                IndicatorParamKeys.FAST_PERIOD, 2, IndicatorParamKeys.SLOW_PERIOD, 3), true);
    }

    @Test
    void rangeAndThresholdFiltersReportConfiguredDirections() {
        var roc = new RocIndicatorEvaluator().evaluate(IndicatorTestSeries.rising(8), IndicatorRole.FILTER,
                Map.of(IndicatorParamKeys.PERIOD, 2, IndicatorParamKeys.MIN_THRESHOLD, 0.0,
                        IndicatorParamKeys.MAX_THRESHOLD, 1000.0));
        assertThat(DirectionBitMask.hasUp(roc.directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(roc.directionMask())).isTrue();

        var ultimate = new UltimateOscillatorIndicatorEvaluator().evaluate(IndicatorTestSeries.rising(8),
                IndicatorRole.FILTER, Map.of(IndicatorParamKeys.PERIOD_1, 2, IndicatorParamKeys.PERIOD_2, 3,
                        IndicatorParamKeys.PERIOD_3, 4, IndicatorParamKeys.LOW_THRESHOLD, 101.0,
                        IndicatorParamKeys.HIGH_THRESHOLD, 102.0));
        assertThat(DirectionBitMask.hasUp(ultimate.directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(ultimate.directionMask())).isFalse();

        var stochasticRvi = new StochRviIndicatorEvaluator().evaluate(IndicatorTestSeries.rising(12),
                IndicatorRole.FILTER, Map.of(IndicatorParamKeys.LENGTH, 1,
                        IndicatorParamKeys.OVERSOLD, 101.0, IndicatorParamKeys.OVERBOUGHT, 102.0));
        assertThat(DirectionBitMask.hasUp(stochasticRvi.directionMask())).isTrue();
    }

    @Test
    void rocAndUltimateOscillatorThresholdTriggersSignalInBothDirections() {
        var roc = new RocIndicatorEvaluator();
        var rocUp = roc.evaluate(IndicatorTestSeries.fromCloses(100, 100, 100, 101), IndicatorRole.TRIGGER,
                Map.of(IndicatorParamKeys.PERIOD, 2, IndicatorParamKeys.CONDITION_TYPE, "CROSS"));
        var rocDown = roc.evaluate(IndicatorTestSeries.fromCloses(100, 100, 100, 99), IndicatorRole.TRIGGER,
                Map.of(IndicatorParamKeys.PERIOD, 2, IndicatorParamKeys.CONDITION_TYPE, "CROSS"));
        assertThat(DirectionBitMask.hasUp(rocUp.directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(rocDown.directionMask())).isTrue();

        var ultimate = new UltimateOscillatorIndicatorEvaluator();
        Map<String, Object> common = Map.of(IndicatorParamKeys.PERIOD_1, 2, IndicatorParamKeys.PERIOD_2, 3,
                IndicatorParamKeys.PERIOD_3, 4);
        var oversold = new java.util.HashMap<>(common);
        oversold.put(IndicatorParamKeys.LOW_THRESHOLD, 101.0);
        oversold.put(IndicatorParamKeys.HIGH_THRESHOLD, 102.0);
        var overbought = new java.util.HashMap<>(common);
        overbought.put(IndicatorParamKeys.LOW_THRESHOLD, -2.0);
        overbought.put(IndicatorParamKeys.HIGH_THRESHOLD, -1.0);
        assertThat(DirectionBitMask.hasUp(ultimate.evaluate(IndicatorTestSeries.rising(7),
                IndicatorRole.TRIGGER, oversold).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(ultimate.evaluate(IndicatorTestSeries.rising(7),
                IndicatorRole.TRIGGER, overbought).directionMask())).isTrue();
    }

    @Test
    void crossoverEvaluatorsRaiseBullishAndBearishSignalsOnReversals() {
        var bullishReversal = IndicatorTestSeries.fromCloses(100, 100, 100, 100, 110);
        var bearishReversal = IndicatorTestSeries.fromCloses(100, 100, 100, 100, 90);

        var smaParams = Map.<String, Object>of(IndicatorParamKeys.FAST_PERIOD, 2,
                IndicatorParamKeys.SLOW_PERIOD, 3);
        assertThat(DirectionBitMask.hasUp(new SmaCrossIndicatorEvaluator()
                .evaluate(bullishReversal, IndicatorRole.TRIGGER, smaParams).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(new SmaCrossIndicatorEvaluator()
                .evaluate(bearishReversal, IndicatorRole.TRIGGER, smaParams).directionMask())).isTrue();

        var macdParams = Map.<String, Object>of(IndicatorParamKeys.FAST_PERIOD, 1,
                IndicatorParamKeys.SLOW_PERIOD, 2, IndicatorParamKeys.SIGNAL_PERIOD, 2);
        assertThat(DirectionBitMask.hasUp(new MacdIndicatorEvaluator()
                .evaluate(bullishReversal, IndicatorRole.TRIGGER, macdParams).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(new MacdIndicatorEvaluator()
                .evaluate(bearishReversal, IndicatorRole.TRIGGER, macdParams).directionMask())).isTrue();

        var aoParams = Map.<String, Object>of(IndicatorParamKeys.FAST_PERIOD, 2,
                IndicatorParamKeys.SLOW_PERIOD, 3);
        assertThat(DirectionBitMask.hasUp(new AoIndicatorEvaluator()
                .evaluate(bullishReversal, IndicatorRole.TRIGGER, aoParams).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(new AoIndicatorEvaluator()
                .evaluate(bearishReversal, IndicatorRole.TRIGGER, aoParams).directionMask())).isTrue();

        var cciParamsUp = Map.<String, Object>of(IndicatorParamKeys.PERIOD, 3,
                IndicatorParamKeys.OVERSOLD, 50.0, IndicatorParamKeys.OVERBOUGHT, 1_000.0);
        var cciParamsDown = Map.<String, Object>of(IndicatorParamKeys.PERIOD, 3,
                IndicatorParamKeys.OVERSOLD, -1_000.0, IndicatorParamKeys.OVERBOUGHT, -50.0);
        assertThat(DirectionBitMask.hasUp(new CciIndicatorEvaluator()
                .evaluate(bullishReversal, IndicatorRole.TRIGGER, cciParamsUp).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(new CciIndicatorEvaluator()
                .evaluate(bearishReversal, IndicatorRole.TRIGGER, cciParamsDown).directionMask())).isTrue();
    }

    @Test
    void cmfCrossesZeroAsBuyingOrSellingPressureChanges() {
        BarSeries buying = IndicatorTestSeries.of(4, index -> 100.0, index -> 110.0, index -> 90.0,
                index -> index < 2 ? 90.0 : 110.0);
        BarSeries selling = IndicatorTestSeries.of(4, index -> 100.0, index -> 110.0, index -> 90.0,
                index -> index < 2 ? 110.0 : 90.0);
        Map<String, Object> params = Map.of(IndicatorParamKeys.PERIOD, 2,
                IndicatorParamKeys.CONDITION_TYPE, "CROSS");

        assertThat(DirectionBitMask.hasUp(new CmfIndicatorEvaluator()
                .evaluate(buying, IndicatorRole.TRIGGER, params).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(new CmfIndicatorEvaluator()
                .evaluate(selling, IndicatorRole.TRIGGER, params).directionMask())).isTrue();
    }

    @Test
    void stochasticAndStochasticRviTriggerOnTheirConfiguredCrosses() {
        var stochastic = new StochasticIndicatorEvaluator();
        BarSeries stochasticUp = IndicatorTestSeries.of(6, index -> 100.0, index -> 110.0, index -> 90.0,
                index -> index < 5 ? 100.0 : 102.0);
        BarSeries stochasticDown = IndicatorTestSeries.of(6, index -> 100.0, index -> 110.0, index -> 90.0,
                index -> index < 5 ? 108.0 : 98.0);
        Map<String, Object> stochasticUpParams = Map.of(IndicatorParamKeys.K_PERIOD, 3,
                IndicatorParamKeys.D_PERIOD, 2, IndicatorParamKeys.OVERSOLD, 101.0,
                IndicatorParamKeys.OVERBOUGHT, 102.0);
        Map<String, Object> stochasticDownParams = Map.of(IndicatorParamKeys.K_PERIOD, 3,
                IndicatorParamKeys.D_PERIOD, 2, IndicatorParamKeys.OVERSOLD, -1.0,
                IndicatorParamKeys.OVERBOUGHT, 0.0);
        assertThat(DirectionBitMask.hasUp(stochastic.evaluate(stochasticUp, IndicatorRole.TRIGGER,
                stochasticUpParams).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(stochastic.evaluate(stochasticDown, IndicatorRole.TRIGGER,
                stochasticDownParams).directionMask())).isTrue();

        var stochRvi = new StochRviIndicatorEvaluator();
        BarSeries stochRviUp = IndicatorTestSeries.of(12, index -> 0.0, index -> 100.0, index -> 0.0,
                index -> index < 8 ? 9.0 - index * 0.8 : index == 11 ? 90.0 : 0.2);
        BarSeries stochRviDown = IndicatorTestSeries.of(12, index -> index == 11 ? 100.0 : 0.0,
                index -> 100.0, index -> 0.0,
                index -> index < 10 ? 0.2 + index * 0.8 : index == 10 ? 9.0 : 0.2);
        var stochRviUpResult = stochRvi.evaluate(stochRviUp, IndicatorRole.TRIGGER,
                Map.of(IndicatorParamKeys.LENGTH, 3, IndicatorParamKeys.OVERSOLD, 30.0,
                        IndicatorParamKeys.OVERBOUGHT, 80.0));
        assertThat(DirectionBitMask.hasUp(stochRviUpResult.directionMask()))
                .as("Stoch RVI bullish trigger %s", stochRviUpResult.snapshot()).isTrue();
        var stochRviDownResult = stochRvi.evaluate(stochRviDown, IndicatorRole.TRIGGER,
                Map.of(IndicatorParamKeys.LENGTH, 3, IndicatorParamKeys.OVERSOLD, 20.0,
                        IndicatorParamKeys.OVERBOUGHT, 80.0));
        assertThat(DirectionBitMask.hasDown(stochRviDownResult.directionMask()))
                .as("Stoch RVI bearish trigger %s", stochRviDownResult.snapshot()).isTrue();
    }

    @Test
    void sarTrendAndContrarianModesUseTheirSpecifiedDirections() {
        SarIndicatorEvaluator evaluator = new SarIndicatorEvaluator();
        assertThat(DirectionBitMask.hasUp(evaluator.evaluate(IndicatorTestSeries.rising(10),
                IndicatorRole.FILTER, Map.of()).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(evaluator.evaluate(IndicatorTestSeries.falling(10),
                IndicatorRole.FILTER, Map.of()).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasUp(evaluator.evaluate(IndicatorTestSeries.falling(10),
                IndicatorRole.FILTER, Map.of(IndicatorParamKeys.MODE, "CONTRARIAN_EXTREME")).directionMask())).isTrue();
        assertThat(DirectionBitMask.hasDown(evaluator.evaluate(IndicatorTestSeries.rising(10),
                IndicatorRole.FILTER, Map.of(IndicatorParamKeys.MODE, "CONTRARIAN_EXTREME")).directionMask())).isTrue();
    }

    @Test
    void macdThresholdLineSelectionUsesSignalMacdOrHistogram() {
        MacdIndicatorEvaluator evaluator = new MacdIndicatorEvaluator();
        for (String line : List.of("SIGNAL", "MACD", "HISTOGRAM")) {
            var result = evaluator.evaluate(IndicatorTestSeries.rising(20), IndicatorRole.FILTER,
                    Map.of(IndicatorParamKeys.FAST_PERIOD, 2, IndicatorParamKeys.SLOW_PERIOD, 5,
                            IndicatorParamKeys.SIGNAL_PERIOD, 3, IndicatorParamKeys.CONDITION_TYPE, "THRESHOLD",
                            IndicatorParamKeys.LINE, line, IndicatorParamKeys.LOW_THRESHOLD, 1_000_000.0,
                            IndicatorParamKeys.HIGH_THRESHOLD, 1_000_001.0));
            assertThat(DirectionBitMask.hasUp(result.directionMask())).isTrue();
            assertThat(result.snapshot()).containsKeys(IndicatorSnapshotKeys.HISTOGRAM);
        }
    }

    @Test
    void macdSnapshotAlwaysContainsTheNumericalHistogram() {
        var result = new MacdIndicatorEvaluator().evaluate(IndicatorTestSeries.rising(20), IndicatorRole.FILTER,
                Map.of(IndicatorParamKeys.FAST_PERIOD, 2, IndicatorParamKeys.SLOW_PERIOD, 5,
                        IndicatorParamKeys.SIGNAL_PERIOD, 3, IndicatorParamKeys.CONDITION_TYPE, "THRESHOLD",
                        IndicatorParamKeys.LINE, "MACD", IndicatorParamKeys.LOW_THRESHOLD, -1.0,
                        IndicatorParamKeys.HIGH_THRESHOLD, 1.0));

        double macd = ((Number) result.snapshot().get(IndicatorSnapshotKeys.MACD)).doubleValue();
        double signal = ((Number) result.snapshot().get(IndicatorSnapshotKeys.SIGNAL)).doubleValue();
        double histogram = ((Number) result.snapshot().get(IndicatorSnapshotKeys.HISTOGRAM)).doubleValue();
        assertThat(histogram).isCloseTo(macd - signal, offset(1.0e-12));
    }

    @Test
    void sarCalculatesTrailingStopAndValidatesPositionSide() {
        SarIndicatorEvaluator evaluator = new SarIndicatorEvaluator();

        assertThat(evaluator.calculateStopLoss(IndicatorTestSeries.rising(1), PositionSide.LONG, Map.of())).isEmpty();
        Optional<java.math.BigDecimal> stop = evaluator.calculateStopLoss(
                IndicatorTestSeries.rising(5), PositionSide.LONG, Map.of());
        assertThat(stop).isPresent();
        assertThat(stop.orElseThrow()).isNotNull();
        assertThatThrownBy(() -> evaluator.calculateStopLoss(IndicatorTestSeries.rising(5), null, Map.of()))
                .isInstanceOf(IndicatorConfigurationException.class)
                .satisfies(exception -> assertThat(((IndicatorConfigurationException) exception).getIndicatorCode())
                        .isEqualTo(IndicatorCode.SAR));
    }

    @Test
    void sarContrarianExtremeModeUsesExtremesAsOppositeDirection() {
        BarSeries rising = IndicatorTestSeries.rising(10);
        var result = new SarIndicatorEvaluator().evaluate(rising, IndicatorRole.FILTER,
                Map.of(IndicatorParamKeys.MODE, "CONTRARIAN_EXTREME"));

        assertThat(result.snapshot()).containsKeys(IndicatorSnapshotKeys.SAR, IndicatorSnapshotKeys.CLOSE);
        assertThat(result.directionMask()).isNotNull();
    }

    @Test
    void invalidPeriodAndThresholdConfigurationsCarryTheirIndicatorCode() {
        assertInvalid(new MacdIndicatorEvaluator(), IndicatorCode.MACD,
                Map.of(IndicatorParamKeys.FAST_PERIOD, 5, IndicatorParamKeys.SLOW_PERIOD, 5));
        assertInvalid(new StochasticIndicatorEvaluator(), IndicatorCode.STOCH,
                Map.of(IndicatorParamKeys.OVERSOLD, 80.0, IndicatorParamKeys.OVERBOUGHT, 20.0));
        assertInvalid(new UltimateOscillatorIndicatorEvaluator(), IndicatorCode.ULTOSC,
                Map.of(IndicatorParamKeys.PERIOD_1, 14, IndicatorParamKeys.PERIOD_2, 7,
                        IndicatorParamKeys.PERIOD_3, 28));
        assertInvalid(new SarIndicatorEvaluator(), IndicatorCode.SAR,
                Map.of(IndicatorParamKeys.ACCELERATION_START, 0.3, IndicatorParamKeys.MAX_ACCELERATION, 0.2));
    }

    private static void assertTrend(IndicatorEvaluator evaluator, Map<String, Object> params, boolean expectTrend) {
        IndicatorEvaluation bullish = evaluator.evaluate(IndicatorTestSeries.rising(60), IndicatorRole.FILTER, params);
        IndicatorEvaluation bearish = evaluator.evaluate(IndicatorTestSeries.falling(60), IndicatorRole.FILTER, params);
        assertDirection(bullish, expectTrend, false);
        assertDirection(bearish, false, expectTrend);
    }

    private static void assertDirection(IndicatorEvaluation result, boolean up, boolean down) {
        assertThat(DirectionBitMask.hasUp(result.directionMask())).isEqualTo(up);
        assertThat(DirectionBitMask.hasDown(result.directionMask())).isEqualTo(down);
    }

    private static void assertInvalid(IndicatorEvaluator evaluator, IndicatorCode code, Map<String, Object> params) {
        assertThatThrownBy(() -> evaluator.validateParams(params))
                .isInstanceOf(IndicatorConfigurationException.class)
                .satisfies(exception -> assertThat(((IndicatorConfigurationException) exception).getIndicatorCode())
                        .isEqualTo(code));
    }

    record EvaluatorCase(IndicatorEvaluator evaluator, Map<String, Object> params, int minimumBars,
                         List<String> snapshotKeys) {
    }
}
