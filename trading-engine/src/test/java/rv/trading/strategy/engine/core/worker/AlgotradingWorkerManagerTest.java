package rv.trading.strategy.engine.core.worker;

import org.awaitility.Awaitility;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.AlgotradingStatus;
import rv.trading.strategy.contracts.enums.BotControlCommand;
import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.SizingMode;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import rv.trading.strategy.contracts.event.BotControlEvent;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.engine.core.indicator.DirectionBitMask;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.MarketHistoryPort;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import rv.trading.strategy.engine.core.signal.SignalBitMaskEvaluator;
import rv.trading.strategy.engine.core.signal.SignalEvaluationResult;
import rv.trading.strategy.engine.core.signal.StrategyConfigValidator;
import rv.trading.strategy.engine.core.sizing.PositionSizer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlgotradingWorkerManagerTest {

    private static final String SYMBOL = "BTCUSD";

    @Mock
    private RuntimeConfigPort runtimeConfigPort;

    @Mock
    private StrategyConfigValidator configValidator;

    @Mock
    private MarketHistoryPort marketHistoryPort;

    @Mock
    private BrokerExecutionPort broker;

    @Mock
    private WorkerPersistenceService persistence;

    @Mock
    private PositionSizer positionSizer;

    @Mock
    private SignalBitMaskEvaluator signalEvaluator;

    @Mock
    private IndicatorRegistry indicatorRegistry;

    private AlgotradingWorkerManager manager;

    @BeforeEach
    void setUp() {
        manager = new AlgotradingWorkerManager(runtimeConfigPort, configValidator, marketHistoryPort,
                broker, persistence, positionSizer, signalEvaluator, indicatorRegistry);
    }

    @AfterEach
    void stopManager() {
        manager.stop();
    }

    @Test
    void startStopAndReloadManageWorkers() {
        AlgotradingRuntimeConfig config = config(7L, 11L, 19L, TimeframeCode.M1);
        when(runtimeConfigPort.loadConfig(config.algotradingId())).thenReturn(Optional.of(config));

        manager.startWorker(config);
        awaitLatestSignalLoads(1);
        manager.stopWorker(config.algotradingId());
        verify(runtimeConfigPort).reportWorkerStatus(config.algotradingId(), AlgotradingStatus.STOPPED, null);

        manager.reloadWorker(config.algotradingId());
        awaitLatestSignalLoads(2);
        verify(runtimeConfigPort).loadConfig(config.algotradingId());
    }

    @Test
    void controlEventsStartStopAndReloadWorkers() {
        AlgotradingRuntimeConfig config = config(7L, 11L, 19L, TimeframeCode.M1);
        when(runtimeConfigPort.loadConfig(config.algotradingId())).thenReturn(Optional.of(config));

        manager.handleControlEvent(controlEvent(config.algotradingId(), BotControlCommand.START));
        awaitLatestSignalLoads(1);
        manager.handleControlEvent(controlEvent(config.algotradingId(), BotControlCommand.STOP));
        verify(runtimeConfigPort).reportWorkerStatus(config.algotradingId(), AlgotradingStatus.STOPPED, null);
        manager.handleControlEvent(controlEvent(config.algotradingId(), BotControlCommand.RELOAD_PARAMS));
        awaitLatestSignalLoads(2);

        verify(runtimeConfigPort, times(2)).loadConfig(config.algotradingId());
    }

    @Test
    void dispatchesTicksByInstrumentAndCandlesByInstrumentTimeframeAndClosedFlag() {
        when(persistence.loadLatestExecutedSignalDirection(anyLong())).thenReturn(Optional.empty());
        when(persistence.loadOpenPosition(anyLong(), anyLong())).thenReturn(Optional.empty());
        when(signalEvaluator.evaluate(any(BarSeries.class), any(AlgotradingRuntimeConfig.class), any()))
                .thenReturn(Instancio.of(SignalEvaluationResult.class)
                        .set(Select.field(SignalEvaluationResult.class, "actions"), List.of())
                        .set(Select.field(SignalEvaluationResult.class, "resolvedDirectionMask"), DirectionBitMask.empty())
                        .set(Select.field(SignalEvaluationResult.class, "indicatorSnapshot"), Map.of())
                        .create());
        AlgotradingRuntimeConfig oneMinute = config(7L, 11L, 19L, TimeframeCode.M1);
        AlgotradingRuntimeConfig oneHour = config(8L, 12L, 19L, TimeframeCode.H1);
        AlgotradingRuntimeConfig otherInstrument = config(9L, 13L, 20L, TimeframeCode.M1);
        manager.startWorker(oneMinute);
        manager.startWorker(oneHour);
        manager.startWorker(otherInstrument);
        awaitLatestSignalLoads(3);
        clearInvocations(persistence, signalEvaluator);

        manager.dispatchTick(tick(19L));
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(signalEvaluator, times(2)).evaluate(any(BarSeries.class), any(), any()));
        verify(persistence, times(2)).loadOpenPosition(anyLong(), eq(19L));
        verify(persistence, never()).loadOpenPosition(anyLong(), eq(20L));

        clearInvocations(persistence, signalEvaluator);
        manager.dispatchTick(tick(21L));
        verify(signalEvaluator, never()).evaluate(any(BarSeries.class), any(), any());
        verify(persistence, never()).loadOpenPosition(anyLong(), anyLong());

        clearInvocations(persistence, signalEvaluator);
        manager.dispatchCandle(candle(19L, TimeframeCode.M1, true));
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(persistence).loadOpenPosition(11L, 19L));
        verify(persistence, never()).loadOpenPosition(12L, 19L);
        verify(persistence, never()).loadOpenPosition(13L, 20L);

        clearInvocations(persistence);
        manager.dispatchCandle(candle(19L, TimeframeCode.H1, true));
        verify(persistence, never()).loadOpenPosition(anyLong(), anyLong());

        clearInvocations(persistence);
        manager.dispatchCandle(candle(21L, TimeframeCode.M1, true));
        verify(persistence, never()).loadOpenPosition(anyLong(), anyLong());

        clearInvocations(persistence);
        manager.dispatchCandle(candle(19L, TimeframeCode.M1, false));
        verify(persistence, never()).loadOpenPosition(anyLong(), anyLong());
    }

    @Test
    void lifecycleStartLoadsActiveConfigs() {
        AlgotradingRuntimeConfig config = config(7L, 11L, 19L, TimeframeCode.M1);
        when(runtimeConfigPort.loadActiveConfigs()).thenReturn(List.of(config));

        manager.start();

        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(persistence).loadLatestExecutedSignalDirection(config.algotradingId()));
        verify(runtimeConfigPort).loadActiveConfigs();
        org.assertj.core.api.Assertions.assertThat(manager.isRunning()).isTrue();
    }

    private void awaitLatestSignalLoads(int count) {
        Awaitility.await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                verify(persistence, times(count)).loadLatestExecutedSignalDirection(anyLong()));
    }

    private static AlgotradingRuntimeConfig config(long algotradingId, long brokerClientId,
                                                    long instrumentPairId, TimeframeCode timeframeCode) {
        return Instancio.of(AlgotradingRuntimeConfig.class)
                .set(Select.field(AlgotradingRuntimeConfig.class, "algotradingId"), algotradingId)
                .set(Select.field(AlgotradingRuntimeConfig.class, "brokerClientId"), brokerClientId)
                .set(Select.field(AlgotradingRuntimeConfig.class, "instrumentPairId"), instrumentPairId)
                .set(Select.field(AlgotradingRuntimeConfig.class, "symbol"), SYMBOL)
                .set(Select.field(AlgotradingRuntimeConfig.class, "timeframeCode"), timeframeCode)
                .set(Select.field(AlgotradingRuntimeConfig.class, "directionMode"), DirectionMode.BOTH)
                .set(Select.field(AlgotradingRuntimeConfig.class, "exitMode"), ExitMode.HYBRID)
                .set(Select.field(AlgotradingRuntimeConfig.class, "sizingMode"), SizingMode.FIXED_VOLUME)
                .set(Select.field(AlgotradingRuntimeConfig.class, "orderVolume"), BigDecimal.ONE)
                .set(Select.field(AlgotradingRuntimeConfig.class, "tickSize"), BigDecimal.ONE)
                .set(Select.field(AlgotradingRuntimeConfig.class, "stepSize"), BigDecimal.ONE)
                .set(Select.field(AlgotradingRuntimeConfig.class, "indicators"), List.of())
                .create();
    }

    private static BotControlEvent controlEvent(long algotradingId, BotControlCommand command) {
        return Instancio.of(BotControlEvent.class)
                .set(Select.field(BotControlEvent.class, "algotradingId"), algotradingId)
                .set(Select.field(BotControlEvent.class, "command"), command)
                .create();
    }

    private static TickEvent tick(long instrumentPairId) {
        return Instancio.of(TickEvent.class)
                .set(Select.field(TickEvent.class, "instrumentPairId"), instrumentPairId)
                .set(Select.field(TickEvent.class, "lastPrice"), BigDecimal.ONE)
                .set(Select.field(TickEvent.class, "timestamp"), Instant.parse("2025-01-01T10:00:00Z"))
                .create();
    }

    private static CandleEvent candle(long instrumentPairId, TimeframeCode timeframeCode, boolean closed) {
        return Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), instrumentPairId)
                .set(Select.field(CandleEvent.class, "timeframeCode"), timeframeCode)
                .set(Select.field(CandleEvent.class, "openTime"), Instant.parse("2025-01-01T10:00:00Z"))
                .set(Select.field(CandleEvent.class, "closeTime"), Instant.parse("2025-01-01T10:01:00Z"))
                .set(Select.field(CandleEvent.class, "open"), BigDecimal.ONE)
                .set(Select.field(CandleEvent.class, "high"), BigDecimal.TEN)
                .set(Select.field(CandleEvent.class, "low"), BigDecimal.ZERO)
                .set(Select.field(CandleEvent.class, "close"), BigDecimal.ONE)
                .set(Select.field(CandleEvent.class, "volume"), BigDecimal.ONE)
                .set(Select.field(CandleEvent.class, "closed"), closed)
                .create();
    }
}
