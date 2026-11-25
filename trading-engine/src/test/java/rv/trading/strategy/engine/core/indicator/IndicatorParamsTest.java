package rv.trading.strategy.engine.core.indicator;

import org.junit.jupiter.api.Test;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorConditionType;
import rv.trading.strategy.engine.core.indicator.exception.IndicatorConfigurationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndicatorParamsTest {

    @Test
    void readsIntegerAndUsesDefaultWhenAbsent() {
        assertThat(IndicatorParams.intValue(IndicatorCode.MACD, Map.of("period", "12"), "period", 9)).isEqualTo(12);
        assertThat(IndicatorParams.intValue(IndicatorCode.MACD, Map.of("period", 4L), "period", 9)).isEqualTo(4);
        assertThat(IndicatorParams.intValue(IndicatorCode.MACD, Map.of(), "period", 9)).isEqualTo(9);
        assertThat(IndicatorParams.positiveInt(IndicatorCode.MACD, Map.of("period", 3), "period", 9)).isEqualTo(3);
    }

    @Test
    void readsDoubleAndUsesDefaultWhenAbsent() {
        assertThat(IndicatorParams.doubleValue(IndicatorCode.CMF, Map.of("threshold", "0.25"), "threshold", 0.0))
                .isEqualTo(0.25);
        assertThat(IndicatorParams.doubleValue(IndicatorCode.CMF, Map.of("threshold", 1), "threshold", 0.0))
                .isEqualTo(1.0);
        assertThat(IndicatorParams.doubleValue(IndicatorCode.CMF, Map.of(), "threshold", 0.5)).isEqualTo(0.5);
    }

    @Test
    void readsEnumCaseInsensitivelyFromStringOrEnumAndUsesDefault() {
        assertThat(IndicatorParams.enumValue(IndicatorCode.MACD, Map.of("mode", "tHrEsHoLd"), "mode",
                IndicatorConditionType.class, IndicatorConditionType.CROSS)).isEqualTo(IndicatorConditionType.THRESHOLD);
        assertThat(IndicatorParams.enumValue(IndicatorCode.MACD, Map.of("mode", IndicatorConditionType.RANGE), "mode",
                IndicatorConditionType.class, IndicatorConditionType.CROSS)).isEqualTo(IndicatorConditionType.RANGE);
        assertThat(IndicatorParams.enumValue(IndicatorCode.MACD, Map.of(), "mode",
                IndicatorConditionType.class, IndicatorConditionType.CROSS)).isEqualTo(IndicatorConditionType.CROSS);
    }

    @Test
    void requireAcceptsTrueAndRaisesDomainConfigurationExceptionForFalse() {
        IndicatorParams.require(IndicatorCode.CCI, true, "valid");

        assertThatThrownBy(() -> IndicatorParams.require(IndicatorCode.CCI, false, "invalid"))
                .isInstanceOf(IndicatorConfigurationException.class)
                .satisfies(exception -> assertThat(((IndicatorConfigurationException) exception).getIndicatorCode())
                        .isEqualTo(IndicatorCode.CCI));
    }

    @Test
    void rejectsNullMalformedFractionalNonPositiveAndUnknownValuesWithIndicatorCode() {
        assertConfigurationFailure(() -> IndicatorParams.intValue(IndicatorCode.ROC,
                java.util.Collections.singletonMap("period", null), "period", 1));
        assertConfigurationFailure(() -> IndicatorParams.intValue(IndicatorCode.ROC,
                Map.of("period", "not-number"), "period", 1));
        assertConfigurationFailure(() -> IndicatorParams.intValue(IndicatorCode.ROC,
                Map.of("period", 2.5), "period", 1));
        assertConfigurationFailure(() -> IndicatorParams.positiveInt(IndicatorCode.ROC,
                Map.of("period", 0), "period", 1));
        assertConfigurationFailure(() -> IndicatorParams.positiveInt(IndicatorCode.ROC,
                Map.of("period", -1), "period", 1));
        assertConfigurationFailure(() -> IndicatorParams.enumValue(IndicatorCode.ROC,
                Map.of("mode", "unknown"), "mode", IndicatorConditionType.class, IndicatorConditionType.CROSS));
    }

    private static void assertConfigurationFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(IndicatorConfigurationException.class)
                .satisfies(exception -> assertThat(((IndicatorConfigurationException) exception).getIndicatorCode())
                        .isEqualTo(IndicatorCode.ROC));
    }
}
