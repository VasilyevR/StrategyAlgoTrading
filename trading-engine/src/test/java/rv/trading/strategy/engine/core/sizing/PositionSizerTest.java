package rv.trading.strategy.engine.core.sizing;

import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.TrailingStopIndicator;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionSizerTest {

    private static final String SYMBOL = "BTCUSD";
    private static final String INDICATOR_ALIAS = "trailing-stop";
    private static final String PARAMS_FIELD = "params";

    private final BrokerExecutionPort broker = mock(BrokerExecutionPort.class);
    private final IndicatorRegistry registry = mock(IndicatorRegistry.class);
    private final TrailingStopIndicator trailingStop = mock(TrailingStopIndicator.class);
    private final BarSeries series = mock(BarSeries.class);
    private final PositionSizer sizer = new PositionSizer(broker, registry);

    @BeforeEach
    void configureTrailingIndicatorLookup() {
        when(registry.getTrailingStopIndicator(IndicatorCode.SAR.name())).thenReturn(Optional.of(trailingStop));
    }

    @Test
    void fixedVolumeRoundsDownToStepSize() {
        BigDecimal quantity = sizer.calculateQuantity(
                config(SizingMode.FIXED_VOLUME, new BigDecimal("10.77"), new BigDecimal("0.25"), List.of()),
                series, PositionSide.LONG, new BigDecimal("100"));

        assertThat(quantity).isEqualByComparingTo("10.75");
    }

    @Test
    void fixedVolumeThrowsWhenRoundedQuantityIsZero() {
        assertThatThrownBy(() -> sizer.calculateQuantity(
                config(SizingMode.FIXED_VOLUME, new BigDecimal("0.09"), new BigDecimal("0.1"), List.of()),
                series, PositionSide.LONG, new BigDecimal("100")))
                .isExactlyInstanceOf(OrderSizingException.class);
    }

    @Test
    void riskPercentUsesLongStopDistanceAndFloorsToStep() {
        when(broker.getAvailableBalance(12L, 24L)).thenReturn(new BigDecimal("1000"));
        when(trailingStop.calculateStopLoss(series, PositionSide.LONG, Map.of()))
                .thenReturn(Optional.of(new BigDecimal("95")));

        BigDecimal quantity = sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, new BigDecimal("0.25"), List.of(stopIndicator())),
                series, PositionSide.LONG, new BigDecimal("100"));

        assertThat(quantity).isEqualByComparingTo("2");
        verify(broker).getAvailableBalance(12L, 24L);
    }

    @Test
    void riskPercentUsesShortStopDistanceAndFloorsToStep() {
        when(broker.getAvailableBalance(12L, 24L)).thenReturn(new BigDecimal("1000"));
        when(trailingStop.calculateStopLoss(series, PositionSide.SHORT, Map.of()))
                .thenReturn(Optional.of(new BigDecimal("105")));

        BigDecimal quantity = sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, new BigDecimal("0.25"), List.of(stopIndicator())),
                series, PositionSide.SHORT, new BigDecimal("100"));

        assertThat(quantity).isEqualByComparingTo("2");
        verify(broker).getAvailableBalance(12L, 24L);
    }

    @Test
    void riskPercentRejectsNonPositiveBalance() {
        when(broker.getAvailableBalance(12L, 24L)).thenReturn(BigDecimal.ZERO);
        when(trailingStop.calculateStopLoss(series, PositionSide.LONG, Map.of()))
                .thenReturn(Optional.of(new BigDecimal("95")));

        assertThatThrownBy(() -> sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, BigDecimal.ONE, List.of(stopIndicator())),
                series, PositionSide.LONG, new BigDecimal("100")))
                .isExactlyInstanceOf(OrderSizingException.class);
    }

    @Test
    void riskPercentRejectsMissingTrailingStop() {
        assertThatThrownBy(() -> sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, BigDecimal.ONE, List.of()),
                series, PositionSide.LONG, new BigDecimal("100")))
                .isExactlyInstanceOf(OrderSizingException.class);
    }

    @Test
    void riskPercentRejectsMissingCalculatedStop() {
        when(trailingStop.calculateStopLoss(series, PositionSide.LONG, Map.of())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, BigDecimal.ONE, List.of(stopIndicator())),
                series, PositionSide.LONG, new BigDecimal("100")))
                .isExactlyInstanceOf(OrderSizingException.class);
    }

    @Test
    void riskPercentRejectsStopOnOrBeyondEntryForEachSide() {
        when(trailingStop.calculateStopLoss(series, PositionSide.LONG, Map.of()))
                .thenReturn(Optional.of(new BigDecimal("100")));
        when(trailingStop.calculateStopLoss(series, PositionSide.SHORT, Map.of()))
                .thenReturn(Optional.of(new BigDecimal("100")));

        assertThatThrownBy(() -> sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, BigDecimal.ONE, List.of(stopIndicator())),
                series, PositionSide.LONG, new BigDecimal("100")))
                .isExactlyInstanceOf(OrderSizingException.class);
        assertThatThrownBy(() -> sizer.calculateQuantity(
                config(SizingMode.RISK_PERCENT, BigDecimal.ONE, BigDecimal.ONE, List.of(stopIndicator())),
                series, PositionSide.SHORT, new BigDecimal("100")))
                .isExactlyInstanceOf(OrderSizingException.class);
    }

    private static StrategyIndicatorConfig stopIndicator() {
        return Instancio.of(StrategyIndicatorConfig.class)
                .set(Select.field(StrategyIndicatorConfig.class, "indicatorCode"), IndicatorCode.SAR)
                .set(Select.field(StrategyIndicatorConfig.class, "alias"), INDICATOR_ALIAS)
                .set(Select.field(StrategyIndicatorConfig.class, "role"), IndicatorRole.TRAILING_STOP)
                .set(Select.field(StrategyIndicatorConfig.class, "conditionGroup"), 1)
                .set(Select.field(StrategyIndicatorConfig.class, PARAMS_FIELD), Map.of())
                .create();
    }

    private static AlgotradingRuntimeConfig config(SizingMode sizingMode, BigDecimal orderVolume,
                                                   BigDecimal stepSize,
                                                   List<StrategyIndicatorConfig> indicators) {
        return Instancio.of(AlgotradingRuntimeConfig.class)
                .set(Select.field(AlgotradingRuntimeConfig.class, "algotradingId"), 6L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "brokerClientId"), 12L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "instrumentPairId"), 24L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "symbol"), SYMBOL)
                .set(Select.field(AlgotradingRuntimeConfig.class, "timeframeCode"), TimeframeCode.M1)
                .set(Select.field(AlgotradingRuntimeConfig.class, "directionMode"), DirectionMode.BOTH)
                .set(Select.field(AlgotradingRuntimeConfig.class, "exitMode"), ExitMode.SIGNAL_MARKET)
                .set(Select.field(AlgotradingRuntimeConfig.class, "sizingMode"), sizingMode)
                .set(Select.field(AlgotradingRuntimeConfig.class, "orderVolume"), orderVolume)
                .set(Select.field(AlgotradingRuntimeConfig.class, "tickSize"), new BigDecimal("0.01"))
                .set(Select.field(AlgotradingRuntimeConfig.class, "stepSize"), stepSize)
                .set(Select.field(AlgotradingRuntimeConfig.class, "indicators"), indicators)
                .create();
    }
}
