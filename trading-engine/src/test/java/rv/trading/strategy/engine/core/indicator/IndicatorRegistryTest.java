package rv.trading.strategy.engine.core.indicator;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.engine.core.indicator.evaluator.AoIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.CciIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.CmfIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.KamaIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.MacdIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.RocIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.SarIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.SmaCrossIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.StochRviIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.StochasticIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.evaluator.UltimateOscillatorIndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class IndicatorRegistryTest {

    @Test
    void registryContainsAllElevenEvaluators() {
        IndicatorRegistry registry = new IndicatorRegistry(List.of(
                new SmaCrossIndicatorEvaluator(), new MacdIndicatorEvaluator(), new CmfIndicatorEvaluator(),
                new SarIndicatorEvaluator(), new AoIndicatorEvaluator(), new UltimateOscillatorIndicatorEvaluator(),
                new CciIndicatorEvaluator(), new RocIndicatorEvaluator(), new KamaIndicatorEvaluator(),
                new StochasticIndicatorEvaluator(), new StochRviIndicatorEvaluator()));

        for (IndicatorCode code : IndicatorCode.values()) {
            assertThat(registry.getEvaluator(code.name()).code()).isEqualTo(code.name());
        }
    }

    @Test
    void unknownAndNullCodesAreConfigurationErrors() {
        IndicatorRegistry registry = new IndicatorRegistry(List.of(new SarIndicatorEvaluator()));
        assertThatThrownBy(() -> registry.getEvaluator("UNKNOWN"))
                .isInstanceOf(IndicatorConfigurationException.class);
        assertThatThrownBy(() -> registry.getEvaluator(null))
                .isInstanceOf(IndicatorConfigurationException.class);
    }

    @Test
    void duplicateCodesFailFast() {
        IndicatorEvaluator first = evaluatorWithCode(IndicatorCode.SMA_CROSS.name());
        IndicatorEvaluator duplicate = evaluatorWithCode(IndicatorCode.SMA_CROSS.name());

        assertThatThrownBy(() -> new IndicatorRegistry(List.of(first, duplicate)))
                .isInstanceOf(IndicatorConfigurationException.class)
                .hasMessageContaining(IndicatorCode.SMA_CROSS.name());
    }

    @Test
    void trailingStopLookupFindsSarOnly() {
        IndicatorRegistry registry = new IndicatorRegistry(List.of(new SarIndicatorEvaluator(), new MacdIndicatorEvaluator()));
        assertThat(registry.getTrailingStopIndicator(IndicatorCode.SAR.name()))
                .containsInstanceOf(SarIndicatorEvaluator.class);
        assertThat(registry.getTrailingStopIndicator(IndicatorCode.MACD.name())).isEmpty();
    }

    @Test
    void explicitlyConstructedEvaluatorSetAlsoMapsCodes() {
        List<IndicatorEvaluator> evaluators = List.of(
                new SmaCrossIndicatorEvaluator(), new MacdIndicatorEvaluator(), new CmfIndicatorEvaluator(),
                new SarIndicatorEvaluator(), new AoIndicatorEvaluator(), new UltimateOscillatorIndicatorEvaluator(),
                new CciIndicatorEvaluator(), new RocIndicatorEvaluator(), new KamaIndicatorEvaluator(),
                new StochasticIndicatorEvaluator(), new StochRviIndicatorEvaluator());
        IndicatorRegistry localRegistry = new IndicatorRegistry(evaluators);

        assertThat(evaluators).extracting(IndicatorEvaluator::code)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(IndicatorCode.values()).map(Enum::name).toArray(String[]::new));
        assertThat(localRegistry.getEvaluator(IndicatorCode.STOCH_RVI.name())).isInstanceOf(StochRviIndicatorEvaluator.class);
    }

    private static IndicatorEvaluator evaluatorWithCode(String code) {
        IndicatorEvaluator evaluator = Mockito.mock(IndicatorEvaluator.class);
        when(evaluator.code()).thenReturn(code);
        return evaluator;
    }
}
