package rv.trading.strategy.engine.core.worker;

import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.AlgotradingStatus;
import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.engine.core.indicator.DirectionBitMask;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.TrailingStopIndicator;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.MarketHistoryPort;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import rv.trading.strategy.engine.core.signal.SignalBitMaskEvaluator;
import rv.trading.strategy.engine.core.signal.SignalEvaluationContext;
import rv.trading.strategy.engine.core.signal.SignalEvaluationResult;
import rv.trading.strategy.engine.core.signal.StrategyConfigValidator;
import rv.trading.strategy.engine.core.sizing.PositionSizer;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.params.provider.Arguments;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class AlgotradingWorkerTest {

    private static final String SYMBOL = "BTCUSD";
    private static final String ALIAS_STOP = "stop";
    private static final String EXCHANGE_STOP_ORDER_ID = "exchange-stop-order";
    private static final String FAILURE_MESSAGE = "invalid strategy configuration";
    private static final BigDecimal ENTRY_PRICE = new BigDecimal("100");
    private static final BigDecimal QUANTITY = new BigDecimal("2.5");

    @Mock
    private StrategyConfigValidator configValidator;

    @Mock
    private MarketHistoryPort marketHistoryPort;

    @Mock
    private BrokerExecutionPort broker;

    @Mock
    private RuntimeConfigPort runtimeConfigPort;

    @Mock
    private WorkerPersistenceService persistence;

    @Mock
    private PositionSizer positionSizer;

    @Mock
    private SignalBitMaskEvaluator signalEvaluator;

    @Mock
    private IndicatorRegistry indicatorRegistry;

    @Mock
    private TrailingStopIndicator trailingStop;

    private AlgotradingWorker worker;
    private AlgotradingRuntimeConfig config;

    @BeforeEach
    void setUp() {
        config = config(ExitMode.SIGNAL_MARKET);
        worker = new AlgotradingWorker();
        worker.initialize(config, new BarSeriesBuffer(), configValidator, marketHistoryPort, broker,
                runtimeConfigPort, persistence, positionSizer, signalEvaluator, indicatorRegistry);
    }

    private void stubSignalPersistence() {
        when(persistence.saveDetectedSignal(any())).thenAnswer(invocation -> {
            SignalEntity signal = invocation.getArgument(0, SignalEntity.class);
            signal.setId(91L);
            signal.setStatus(SignalStatus.DETECTED);
            return signal;
        });
        when(persistence.updateSignalStatus(any(), any(), any())).thenAnswer(invocation -> {
            SignalEntity signal = invocation.getArgument(0, SignalEntity.class);
            signal.setStatus(invocation.getArgument(1, SignalStatus.class));
            signal.setErrorMessage(invocation.getArgument(2, String.class));
            return signal;
        });
    }

    private void stubOrderPersistence() {
        doAnswer(invocation -> invocation.getArgument(0)).when(persistence).saveOrder(any());
    }

    private void stubPositionPersistence() {
        doAnswer(invocation -> {
            PositionEntity position = invocation.getArgument(0, PositionEntity.class);
            if (position.getId() == null) {
                position.setId(52L);
            }
            return position;
        }).when(persistence).savePosition(any());
    }

    private void stubTrailingIndicator() {
        when(indicatorRegistry.getTrailingStopIndicator(IndicatorCode.SAR.name()))
                .thenReturn(Optional.of(trailingStop));
    }

    @Test
    void warmUpValidatesLoadsHistoryRestoresDirectionAndReportsActiveWithoutSignals() {
        List<CandleEvent> history = List.of(candle(Instant.parse("2025-01-01T10:00:00Z"),
                BigDecimal.valueOf(99), BigDecimal.valueOf(101), BigDecimal.valueOf(98), ENTRY_PRICE));
        when(marketHistoryPort.loadRecentCandles(
                config.instrumentPairId(), config.timeframeCode(), BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT))
                .thenReturn(history);
        when(persistence.loadLatestExecutedSignalDirection(config.algotradingId()))
                .thenReturn(Optional.of(OrderSide.SELL));
        when(persistence.loadOpenPosition(config.brokerClientId(), config.instrumentPairId()))
                .thenReturn(Optional.empty());
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(config), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(), DirectionBitMask.empty()));

        worker.warmUp();
        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE));

        verify(configValidator).validate(config);
        verify(marketHistoryPort).loadRecentCandles(
                config.instrumentPairId(), config.timeframeCode(), BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT);
        verify(persistence).loadLatestExecutedSignalDirection(config.algotradingId());
        verify(runtimeConfigPort).reportWorkerStatus(config.algotradingId(), AlgotradingStatus.ACTIVE, null);
        verify(persistence, never()).saveDetectedSignal(any());
        ArgumentCaptor<SignalEvaluationContext> context = ArgumentCaptor.forClass(SignalEvaluationContext.class);
        verify(signalEvaluator).evaluate(any(BarSeries.class), eq(config), context.capture());
        assertThat(context.getValue().lastSignalDirection()).contains(OrderSide.SELL);
    }

    @Test
    void warmUpReportsErrorAndStopsWhenConfigurationValidationFails() {
        doThrow(new IllegalArgumentException(FAILURE_MESSAGE)).when(configValidator).validate(config);

        worker.warmUp();

        assertThat(worker.isStopped()).isTrue();
        verify(marketHistoryPort, never()).loadRecentCandles(anyLong(), any(), anyInt());
        verify(runtimeConfigPort).reportWorkerStatus(
                config.algotradingId(), AlgotradingStatus.ERROR, FAILURE_MESSAGE);
    }

    @Test
    void accountWideOpenPositionIgnoresNewEntryWithoutBrokerOrder() {
        stubSignalPersistence();
        PositionEntity openPosition = position(PositionSide.SHORT, new BigDecimal("95"));
        doReturn(Optional.empty()).doReturn(Optional.of(openPosition)).when(persistence)
                .loadOpenPosition(config.brokerClientId(), config.instrumentPairId());
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(config), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(SignalType.BUY_OPEN), DirectionBitMask.of(true, false)));

        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE));

        verify(persistence).updateSignalStatus(any(SignalEntity.class), eq(SignalStatus.IGNORED), eq(null));
        verify(broker, never()).placeOrder(any());
    }

    @Test
    void accountWideActiveOrderIgnoresNewEntryWithoutBrokerOrder() {
        stubSignalPersistence();
        doReturn(Optional.empty()).doReturn(Optional.empty()).when(persistence)
                .loadOpenPosition(config.brokerClientId(), config.instrumentPairId());
        when(persistence.hasActiveOrders(config.brokerClientId(), config.instrumentPairId())).thenReturn(true);
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(config), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(SignalType.SELL_OPEN), DirectionBitMask.of(false, true)));

        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE));

        verify(persistence).updateSignalStatus(any(SignalEntity.class), eq(SignalStatus.IGNORED), eq(null));
        verify(broker, never()).placeOrder(any());
    }

    @Test
    void closeAndReversalCancelsLongStopClosesLongThenOpensShortWithStop() {
        stubSignalPersistence();
        stubOrderPersistence();
        stubPositionPersistence();
        stubTrailingIndicator();
        AlgotradingRuntimeConfig hybridConfig = config(ExitMode.HYBRID);
        worker.initialize(hybridConfig, new BarSeriesBuffer(), configValidator, marketHistoryPort, broker,
                runtimeConfigPort, persistence, positionSizer, signalEvaluator, indicatorRegistry);
        PositionEntity longPosition = position(PositionSide.LONG, new BigDecimal("95"));
        longPosition.setTotalCommission(BigDecimal.ZERO);
        OrderEntity existingStop = Instancio.create(OrderEntity.class);
        existingStop.setId(38L);
        existingStop.setExternalOrderId(EXCHANGE_STOP_ORDER_ID);
        doReturn(Optional.of(longPosition)).doReturn(Optional.of(longPosition)).doReturn(Optional.empty())
                .when(persistence).loadOpenPosition(hybridConfig.brokerClientId(), hybridConfig.instrumentPairId());
        when(persistence.loadStopOrder(longPosition.getId())).thenReturn(Optional.of(existingStop));
        when(persistence.hasActiveOrders(hybridConfig.brokerClientId(), hybridConfig.instrumentPairId()))
                .thenReturn(false);
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(hybridConfig), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(SignalType.BUY_CLOSE, SignalType.SELL_OPEN),
                        DirectionBitMask.of(false, true)));
        when(positionSizer.calculateQuantity(eq(hybridConfig), any(BarSeries.class), eq(PositionSide.SHORT), any()))
                .thenReturn(QUANTITY);
        when(trailingStop.calculateStopLoss(any(BarSeries.class), eq(PositionSide.SHORT), any()))
                .thenReturn(Optional.of(new BigDecimal("110.04")));
        doReturn(receipt("close-order", new BigDecimal("2.5"), new BigDecimal("110"), new BigDecimal("0.5")))
                .doReturn(receipt("open-order", QUANTITY, new BigDecimal("90"), new BigDecimal("0.2")))
                .doReturn(receipt("stop-order", BigDecimal.ZERO, null, BigDecimal.ZERO))
                .when(broker).placeOrder(any());

        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), new BigDecimal("110")));

        ArgumentCaptor<PlaceOrderCommand> commands = ArgumentCaptor.forClass(PlaceOrderCommand.class);
        verify(broker, times(3)).placeOrder(commands.capture());
        assertThat(commands.getAllValues()).extracting(PlaceOrderCommand::orderType)
                .containsExactly(OrderType.MARKET, OrderType.MARKET, OrderType.STOP);
        assertThat(commands.getAllValues().get(0).side()).isEqualTo(OrderSide.SELL);
        assertThat(commands.getAllValues().get(0).reduceOnly()).isTrue();
        assertThat(commands.getAllValues().get(1).side()).isEqualTo(OrderSide.SELL);
        assertThat(commands.getAllValues().get(2).side()).isEqualTo(OrderSide.BUY);
        assertThat(commands.getAllValues().get(2).stopPrice()).isEqualByComparingTo("110.1");
        verify(broker).cancelOrder(hybridConfig.brokerClientId(), hybridConfig.instrumentPairId(),
                EXCHANGE_STOP_ORDER_ID);
        assertThat(longPosition.getStatus()).isEqualTo(PositionStatus.CLOSED);
        assertThat(longPosition.getRealizedPnl()).isEqualByComparingTo("25.0");
        ArgumentCaptor<PositionEntity> savedPositions = ArgumentCaptor.forClass(PositionEntity.class);
        verify(persistence, times(3)).savePosition(savedPositions.capture());
        assertThat(savedPositions.getAllValues().get(2).getSide()).isEqualTo(PositionSide.SHORT);
        assertThat(savedPositions.getAllValues().get(2).getCurrentStopLoss()).isEqualByComparingTo("110.1");
        verify(persistence).updateSignalStatus(any(SignalEntity.class), eq(SignalStatus.EXECUTED), eq(null));
    }

    @Test
    void candleCloseAmendsLongStopOnlyWhenRoundedStopMovesFavorablyAndRemainsBelowClose() {
        stubTrailingIndicator();
        AlgotradingRuntimeConfig hybridConfig = config(ExitMode.HYBRID);
        worker.initialize(hybridConfig, new BarSeriesBuffer(), configValidator, marketHistoryPort, broker,
                runtimeConfigPort, persistence, positionSizer, signalEvaluator, indicatorRegistry);
        PositionEntity longPosition = position(PositionSide.LONG, new BigDecimal("95.0"));
        OrderEntity stopOrder = Instancio.create(OrderEntity.class);
        when(persistence.loadOpenPosition(hybridConfig.brokerClientId(), hybridConfig.instrumentPairId()))
                .thenReturn(Optional.of(longPosition));
        when(persistence.loadStopOrder(longPosition.getId())).thenReturn(Optional.of(stopOrder));
        when(trailingStop.calculateStopLoss(any(BarSeries.class), eq(PositionSide.LONG), any()))
                .thenReturn(Optional.of(new BigDecimal("95.26")));

        worker.onCandleClosed(candle(Instant.parse("2025-01-01T10:00:00Z"),
                new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("94"),
                new BigDecimal("100")));

        ArgumentCaptor<AmendStopLossCommand> amend = ArgumentCaptor.forClass(AmendStopLossCommand.class);
        verify(broker).amendStopLoss(amend.capture());
        assertThat(amend.getValue().newStopPrice()).isEqualByComparingTo("95.2");
        assertThat(longPosition.getCurrentStopLoss()).isEqualByComparingTo("95.2");
        assertThat(stopOrder.getStopPrice()).isEqualByComparingTo("95.2");
    }

    @Test
    void candleCloseAmendsShortStopOnlyWhenRoundedStopMovesFavorablyAndRemainsAboveClose() {
        stubTrailingIndicator();
        AlgotradingRuntimeConfig hybridConfig = config(ExitMode.HYBRID);
        worker.initialize(hybridConfig, new BarSeriesBuffer(), configValidator, marketHistoryPort, broker,
                runtimeConfigPort, persistence, positionSizer, signalEvaluator, indicatorRegistry);
        PositionEntity shortPosition = position(PositionSide.SHORT, new BigDecimal("105.0"));
        OrderEntity stopOrder = Instancio.create(OrderEntity.class);
        when(persistence.loadOpenPosition(hybridConfig.brokerClientId(), hybridConfig.instrumentPairId()))
                .thenReturn(Optional.of(shortPosition));
        when(persistence.loadStopOrder(shortPosition.getId())).thenReturn(Optional.of(stopOrder));
        when(trailingStop.calculateStopLoss(any(BarSeries.class), eq(PositionSide.SHORT), any()))
                .thenReturn(Optional.of(new BigDecimal("104.74")));

        worker.onCandleClosed(candle(Instant.parse("2025-01-01T10:00:00Z"),
                new BigDecimal("100"), new BigDecimal("106"), new BigDecimal("99"),
                new BigDecimal("100")));

        ArgumentCaptor<AmendStopLossCommand> amend = ArgumentCaptor.forClass(AmendStopLossCommand.class);
        verify(broker).amendStopLoss(amend.capture());
        assertThat(amend.getValue().newStopPrice()).isEqualByComparingTo("104.8");
        assertThat(shortPosition.getCurrentStopLoss()).isEqualByComparingTo("104.8");
        assertThat(stopOrder.getStopPrice()).isEqualByComparingTo("104.8");
    }

    @Test
    void candleCloseDoesNotAmendStopWhenItMovesAgainstPositionOrCrossesMarket() {
        stubTrailingIndicator();
        AlgotradingRuntimeConfig hybridConfig = config(ExitMode.HYBRID);
        worker.initialize(hybridConfig, new BarSeriesBuffer(), configValidator, marketHistoryPort, broker,
                runtimeConfigPort, persistence, positionSizer, signalEvaluator, indicatorRegistry);
        PositionEntity longPosition = position(PositionSide.LONG, new BigDecimal("95"));
        when(persistence.loadOpenPosition(hybridConfig.brokerClientId(), hybridConfig.instrumentPairId()))
                .thenReturn(Optional.of(longPosition));
        when(trailingStop.calculateStopLoss(any(BarSeries.class), eq(PositionSide.LONG), any()))
                .thenReturn(Optional.of(new BigDecimal("94")));

        worker.onCandleClosed(candle(Instant.parse("2025-01-01T10:00:00Z"), ENTRY_PRICE,
                new BigDecimal("102"), new BigDecimal("93"), ENTRY_PRICE));

        verify(broker, never()).amendStopLoss(any());

        when(trailingStop.calculateStopLoss(any(BarSeries.class), eq(PositionSide.LONG), any()))
                .thenReturn(Optional.of(new BigDecimal("101")));
        worker.onCandleClosed(candle(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE,
                new BigDecimal("102"), new BigDecimal("93"), ENTRY_PRICE));

        verify(broker, never()).amendStopLoss(any());
    }

    @Test
    void executedActionDirectionMemoryUsesBullishOrBearishMarketDirection() {
        stubSignalPersistence();
        stubPositionPersistence();
        doReturn(Optional.empty()).doReturn(Optional.empty()).doReturn(Optional.empty()).when(persistence)
                .loadOpenPosition(config.brokerClientId(), config.instrumentPairId());
        when(persistence.hasActiveOrders(config.brokerClientId(), config.instrumentPairId())).thenReturn(false);
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(config), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(SignalType.BUY_OPEN), DirectionBitMask.of(true, false)))
                .thenReturn(evaluation(List.of(), DirectionBitMask.empty()));
        when(positionSizer.calculateQuantity(eq(config), any(BarSeries.class), eq(PositionSide.LONG), any()))
                .thenReturn(QUANTITY);
        when(broker.placeOrder(any())).thenReturn(receipt("open-order", QUANTITY, ENTRY_PRICE, BigDecimal.ZERO));

        worker.onTick(tick(Instant.parse("2025-01-01T10:00:00Z"), ENTRY_PRICE));
        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE));

        ArgumentCaptor<SignalEvaluationContext> context = ArgumentCaptor.forClass(SignalEvaluationContext.class);
        verify(signalEvaluator, times(2)).evaluate(any(BarSeries.class), eq(config), context.capture());
        assertThat(context.getAllValues().get(1).lastSignalDirection()).contains(OrderSide.BUY);
    }

    @ParameterizedTest
    @MethodSource("closeSignalDirections")
    void closeSignalDirectionMemoryTracksTheMarketSignal(SignalType closeAction, PositionSide positionSide,
                                                         OrderSide expectedDirection) {
        stubSignalPersistence();
        stubOrderPersistence();
        PositionEntity openPosition = position(positionSide, new BigDecimal("95"));
        doReturn(Optional.of(openPosition)).doReturn(Optional.of(openPosition)).doReturn(Optional.empty())
                .when(persistence).loadOpenPosition(config.brokerClientId(), config.instrumentPairId());
        when(persistence.loadStopOrder(openPosition.getId())).thenReturn(Optional.empty());
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(config), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(closeAction), DirectionBitMask.empty()))
                .thenReturn(evaluation(List.of(), DirectionBitMask.empty()));
        when(broker.placeOrder(any())).thenReturn(receipt("close-order", QUANTITY, ENTRY_PRICE, BigDecimal.ZERO));

        worker.onTick(tick(Instant.parse("2025-01-01T10:00:00Z"), ENTRY_PRICE));
        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE));

        ArgumentCaptor<SignalEvaluationContext> context = ArgumentCaptor.forClass(SignalEvaluationContext.class);
        verify(signalEvaluator, times(2)).evaluate(any(BarSeries.class), eq(config), context.capture());
        assertThat(context.getAllValues().get(1).lastSignalDirection()).contains(expectedDirection);
    }

    @Test
    void emptyActionsWithoutPositionRefreshDirectionFromResolvedMask() {
        when(marketHistoryPort.loadRecentCandles(
                config.instrumentPairId(), config.timeframeCode(), BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT))
                .thenReturn(List.of());
        when(persistence.loadLatestExecutedSignalDirection(config.algotradingId()))
                .thenReturn(Optional.of(OrderSide.SELL));
        doReturn(Optional.empty()).doReturn(Optional.empty()).when(persistence)
                .loadOpenPosition(config.brokerClientId(), config.instrumentPairId());
        when(signalEvaluator.evaluate(any(BarSeries.class), eq(config), any(SignalEvaluationContext.class)))
                .thenReturn(evaluation(List.of(), DirectionBitMask.of(true, false)))
                .thenReturn(evaluation(List.of(), DirectionBitMask.empty()));

        worker.warmUp();
        worker.onTick(tick(Instant.parse("2025-01-01T10:00:00Z"), ENTRY_PRICE));
        worker.onTick(tick(Instant.parse("2025-01-01T10:01:00Z"), ENTRY_PRICE));

        ArgumentCaptor<SignalEvaluationContext> context = ArgumentCaptor.forClass(SignalEvaluationContext.class);
        verify(signalEvaluator, times(2)).evaluate(any(BarSeries.class), eq(config), context.capture());
        assertThat(context.getAllValues().get(1).lastSignalDirection()).contains(OrderSide.BUY);
    }

    private static Stream<Arguments> closeSignalDirections() {
        return Stream.of(
                Arguments.of(SignalType.BUY_CLOSE, PositionSide.LONG, OrderSide.SELL),
                Arguments.of(SignalType.SELL_CLOSE, PositionSide.SHORT, OrderSide.BUY)
        );
    }

    private static SignalEvaluationResult evaluation(List<SignalType> actions, java.util.BitSet mask) {
        return Instancio.of(SignalEvaluationResult.class)
                .set(Select.field(SignalEvaluationResult.class, "actions"), actions)
                .set(Select.field(SignalEvaluationResult.class, "resolvedDirectionMask"), mask)
                .set(Select.field(SignalEvaluationResult.class, "indicatorSnapshot"), Map.of())
                .create();
    }

    private static AlgotradingRuntimeConfig config(ExitMode exitMode) {
        StrategyIndicatorConfig stopIndicator = Instancio.of(StrategyIndicatorConfig.class)
                .set(Select.field(StrategyIndicatorConfig.class, "indicatorCode"), IndicatorCode.SAR)
                .set(Select.field(StrategyIndicatorConfig.class, "alias"), ALIAS_STOP)
                .set(Select.field(StrategyIndicatorConfig.class, "role"), IndicatorRole.TRAILING_STOP)
                .set(Select.field(StrategyIndicatorConfig.class, "conditionGroup"), 1)
                .set(Select.field(StrategyIndicatorConfig.class, "params"), Map.of())
                .create();
        return Instancio.of(AlgotradingRuntimeConfig.class)
                .set(Select.field(AlgotradingRuntimeConfig.class, "algotradingId"), 7L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "brokerClientId"), 11L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "instrumentPairId"), 19L)
                .set(Select.field(AlgotradingRuntimeConfig.class, "symbol"), SYMBOL)
                .set(Select.field(AlgotradingRuntimeConfig.class, "timeframeCode"), TimeframeCode.M1)
                .set(Select.field(AlgotradingRuntimeConfig.class, "directionMode"), DirectionMode.BOTH)
                .set(Select.field(AlgotradingRuntimeConfig.class, "exitMode"), exitMode)
                .set(Select.field(AlgotradingRuntimeConfig.class, "sizingMode"), SizingMode.FIXED_VOLUME)
                .set(Select.field(AlgotradingRuntimeConfig.class, "orderVolume"), QUANTITY)
                .set(Select.field(AlgotradingRuntimeConfig.class, "tickSize"), new BigDecimal("0.1"))
                .set(Select.field(AlgotradingRuntimeConfig.class, "stepSize"), new BigDecimal("0.1"))
                .set(Select.field(AlgotradingRuntimeConfig.class, "indicators"), List.of(stopIndicator))
                .create();
    }

    private static PositionEntity position(PositionSide side, BigDecimal stop) {
        PositionEntity position = Instancio.create(PositionEntity.class);
        position.setId(23L);
        position.setBrokerClientId(11L);
        position.setInstrumentPairId(19L);
        position.setSide(side);
        position.setStatus(PositionStatus.OPEN);
        position.setQuantity(QUANTITY);
        position.setAvgEntryPrice(ENTRY_PRICE);
        position.setCurrentStopLoss(stop);
        position.setRealizedPnl(BigDecimal.ZERO);
        position.setTotalCommission(BigDecimal.ZERO);
        position.setCurrency(SYMBOL);
        return position;
    }

    private static OrderExecutionReceipt receipt(String exchangeOrderId, BigDecimal filledQuantity,
                                                 BigDecimal avgFillPrice, BigDecimal commission) {
        return Instancio.of(OrderExecutionReceipt.class)
                .set(Select.field(OrderExecutionReceipt.class, "exchangeOrderId"), exchangeOrderId)
                .set(Select.field(OrderExecutionReceipt.class, "status"), OrderStatus.FILLED)
                .set(Select.field(OrderExecutionReceipt.class, "filledQuantity"), filledQuantity)
                .set(Select.field(OrderExecutionReceipt.class, "avgFillPrice"), avgFillPrice)
                .set(Select.field(OrderExecutionReceipt.class, "commission"), commission)
                .create();
    }

    private static TickEvent tick(Instant timestamp, BigDecimal price) {
        return Instancio.of(TickEvent.class)
                .set(Select.field(TickEvent.class, "instrumentPairId"), 19L)
                .set(Select.field(TickEvent.class, "symbol"), SYMBOL)
                .set(Select.field(TickEvent.class, "lastPrice"), price)
                .set(Select.field(TickEvent.class, "timestamp"), timestamp)
                .create();
    }

    private static CandleEvent candle(Instant openTime, BigDecimal open, BigDecimal high,
                                      BigDecimal low, BigDecimal close) {
        return Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), 19L)
                .set(Select.field(CandleEvent.class, "symbol"), SYMBOL)
                .set(Select.field(CandleEvent.class, "timeframeCode"), TimeframeCode.M1)
                .set(Select.field(CandleEvent.class, "openTime"), openTime)
                .set(Select.field(CandleEvent.class, "closeTime"), openTime.plusSeconds(60))
                .set(Select.field(CandleEvent.class, "open"), open)
                .set(Select.field(CandleEvent.class, "high"), high)
                .set(Select.field(CandleEvent.class, "low"), low)
                .set(Select.field(CandleEvent.class, "close"), close)
                .set(Select.field(CandleEvent.class, "volume"), BigDecimal.ONE)
                .set(Select.field(CandleEvent.class, "closed"), true)
                .create();
    }
}
