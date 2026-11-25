package rv.trading.strategy.engine.core.worker;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.event.BotControlEvent;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.MarketHistoryPort;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import rv.trading.strategy.engine.core.signal.SignalBitMaskEvaluator;
import rv.trading.strategy.engine.core.signal.StrategyConfigValidator;
import rv.trading.strategy.engine.core.sizing.PositionSizer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

@Component
public class AlgotradingWorkerManager implements SmartLifecycle {
    private final ConcurrentHashMap<Long, AlgotradingWorker> workers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final RuntimeConfigPort runtimeConfigPort;
    private final StrategyConfigValidator configValidator;
    private final MarketHistoryPort marketHistoryPort;
    private final BrokerExecutionPort brokerExecutionPort;
    private final WorkerPersistenceService persistenceService;
    private final PositionSizer positionSizer;
    private final SignalBitMaskEvaluator signalEvaluator;
    private final IndicatorRegistry indicatorRegistry;
    private volatile boolean running;

    public AlgotradingWorkerManager(RuntimeConfigPort runtimeConfigPort,
                                    StrategyConfigValidator configValidator,
                                    MarketHistoryPort marketHistoryPort,
                                    BrokerExecutionPort brokerExecutionPort,
                                    WorkerPersistenceService persistenceService,
                                    PositionSizer positionSizer,
                                    SignalBitMaskEvaluator signalEvaluator,
                                    IndicatorRegistry indicatorRegistry) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.runtimeConfigPort = runtimeConfigPort;
        this.configValidator = configValidator;
        this.marketHistoryPort = marketHistoryPort;
        this.brokerExecutionPort = brokerExecutionPort;
        this.persistenceService = persistenceService;
        this.positionSizer = positionSizer;
        this.signalEvaluator = signalEvaluator;
        this.indicatorRegistry = indicatorRegistry;
    }

    public void startWorker(AlgotradingRuntimeConfig config) {
        if (config == null) {
            return;
        }
        AlgotradingWorker worker = new AlgotradingWorker();
        worker.initialize(config, new BarSeriesBuffer(), configValidator, marketHistoryPort,
                brokerExecutionPort, runtimeConfigPort, persistenceService, positionSizer,
                signalEvaluator, indicatorRegistry);
        workers.put(config.algotradingId(), worker);
        executor.execute(worker::warmUp);
    }

    public void stopWorker(long algotradingId) {
        AlgotradingWorker worker = workers.remove(algotradingId);
        if (worker != null) {
            worker.stop();
        }
    }

    public void reloadWorker(long algotradingId) {
        stopWorker(algotradingId);
        runtimeConfigPort.loadConfig(algotradingId).ifPresent(this::startWorker);
    }

    public void dispatchTick(TickEvent tick) {
        if (tick == null) {
            return;
        }
        for (AlgotradingWorker worker : workers.values()) {
            AlgotradingRuntimeConfig config = worker.getConfig();
            if (config != null && config.instrumentPairId() == tick.instrumentPairId()) {
                executor.execute(() -> worker.onTick(tick));
            }
        }
    }

    public void dispatchCandle(CandleEvent candle) {
        if (candle == null) {
            return;
        }
        for (AlgotradingWorker worker : workers.values()) {
            AlgotradingRuntimeConfig config = worker.getConfig();
            if (config != null && config.instrumentPairId() == candle.instrumentPairId()
                    && config.timeframeCode() == candle.timeframeCode() && candle.closed()) {
                executor.execute(() -> worker.onCandleClosed(candle));
            }
        }
    }

    public void handleControlEvent(BotControlEvent event) {
        if (event == null || event.command() == null) {
            return;
        }
        switch (event.command()) {
            case START -> runtimeConfigPort.loadConfig(event.algotradingId())
                    .ifPresent(this::startWorker);
            case STOP -> stopWorker(event.algotradingId());
            case RELOAD_PARAMS -> reloadWorker(event.algotradingId());
        }
    }

    @Override
    public void start() {
        running = true;
        for (AlgotradingRuntimeConfig config : runtimeConfigPort.loadActiveConfigs()) {
            startWorker(config);
        }
    }

    @Override
    public void stop() {
        running = false;
        for (Long algotradingId : List.copyOf(workers.keySet())) {
            stopWorker(algotradingId);
        }
        executor.close();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
