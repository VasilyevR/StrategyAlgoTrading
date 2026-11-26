package rv.trading.strategy.engine.core.worker;

import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
import rv.trading.strategy.engine.core.indicator.IndicatorParamKeys;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.evaluator.SmaCrossIndicatorEvaluator;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.MarketHistoryPort;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import rv.trading.strategy.engine.core.signal.SignalBitMaskEvaluator;
import rv.trading.strategy.engine.core.signal.StrategyConfigValidator;
import rv.trading.strategy.engine.core.sizing.PositionSizer;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TradingViewSmaParityFunctionalTest {

    private static final String SYMBOL = "NFLX";
    private static final String FIXTURE_PATH = "/fixtures/nflx-1d-tv-sample.csv";
    private static final long ALGOTRADING_ID = 1L;
    private static final long BROKER_CLIENT_ID = 100L;
    private static final long INSTRUMENT_PAIR_ID = 200L;
    private static final BigDecimal FIXED_VOLUME = new BigDecimal("1.0");

    private AlgotradingWorker worker;
    private InMemoryWorkerPersistenceService persistence;
    private InMemoryBrokerExecutionPort broker;
    private BarSeriesBuffer buffer;
    private AlgotradingRuntimeConfig config;
    private List<CandleFixture> candles;

    private record CandleFixture(
            Instant time,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            double tvSma9,
            double tvSma18,
            CandleEvent event
    ) {}

    @BeforeEach
    void setUp() {
        candles = loadFixtures();
        SmaCrossIndicatorEvaluator smaEvaluator = new SmaCrossIndicatorEvaluator();
        IndicatorRegistry registry = new IndicatorRegistry(List.of(smaEvaluator));
        StrategyConfigValidator validator = new StrategyConfigValidator(registry);
        SignalBitMaskEvaluator evaluator = new SignalBitMaskEvaluator(registry);

        persistence = new InMemoryWorkerPersistenceService();
        broker = new InMemoryBrokerExecutionPort();
        PositionSizer positionSizer = new PositionSizer(broker, registry);

        StrategyIndicatorConfig smaConfig = Instancio.of(StrategyIndicatorConfig.class)
                .set(Select.field(StrategyIndicatorConfig.class, "indicatorCode"), IndicatorCode.SMA_CROSS)
                .set(Select.field(StrategyIndicatorConfig.class, "alias"), "sma_cross")
                .set(Select.field(StrategyIndicatorConfig.class, "role"), IndicatorRole.TRIGGER)
                .set(Select.field(StrategyIndicatorConfig.class, "conditionGroup"), 1)
                .set(Select.field(StrategyIndicatorConfig.class, "params"), Map.of(
                        IndicatorParamKeys.FAST_PERIOD, 9,
                        IndicatorParamKeys.SLOW_PERIOD, 18
                ))
                .create();

        config = Instancio.of(AlgotradingRuntimeConfig.class)
                .set(Select.field(AlgotradingRuntimeConfig.class, "algotradingId"), ALGOTRADING_ID)
                .set(Select.field(AlgotradingRuntimeConfig.class, "brokerClientId"), BROKER_CLIENT_ID)
                .set(Select.field(AlgotradingRuntimeConfig.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                .set(Select.field(AlgotradingRuntimeConfig.class, "symbol"), SYMBOL)
                .set(Select.field(AlgotradingRuntimeConfig.class, "timeframeCode"), TimeframeCode.D1)
                .set(Select.field(AlgotradingRuntimeConfig.class, "directionMode"), DirectionMode.BOTH)
                .set(Select.field(AlgotradingRuntimeConfig.class, "exitMode"), ExitMode.SIGNAL_MARKET)
                .set(Select.field(AlgotradingRuntimeConfig.class, "sizingMode"), SizingMode.FIXED_VOLUME)
                .set(Select.field(AlgotradingRuntimeConfig.class, "orderVolume"), FIXED_VOLUME)
                .set(Select.field(AlgotradingRuntimeConfig.class, "tickSize"), new BigDecimal("0.01"))
                .set(Select.field(AlgotradingRuntimeConfig.class, "stepSize"), new BigDecimal("1.0"))
                .set(Select.field(AlgotradingRuntimeConfig.class, "indicators"), List.of(smaConfig))
                .create();

        MarketHistoryPort marketHistory = (instrumentPairId, timeframeCode, limit) -> candles.subList(0, 18)
                .stream()
                .map(CandleFixture::event)
                .toList();
        RuntimeConfigPort runtimeConfig = new RuntimeConfigPort() {
            @Override
            public List<AlgotradingRuntimeConfig> loadActiveConfigs() {
                return List.of(config);
            }

            @Override
            public Optional<AlgotradingRuntimeConfig> loadConfig(long algotradingId) {
                return Optional.of(config);
            }

            @Override
            public void reportWorkerStatus(long algotradingId, AlgotradingStatus status, String errorMessage) {
            }
        };

        buffer = new BarSeriesBuffer();
        worker = new AlgotradingWorker();
        worker.initialize(config, buffer, validator, marketHistory, broker, runtimeConfig,
                persistence, positionSizer, evaluator, registry);
    }

    @Test
    void tradingViewSmaParityTest() {
        worker.warmUp();
        assertThat(worker.isStopped()).isFalse();

        ClosePriceIndicator closePrice = new ClosePriceIndicator(buffer.series());
        SMAIndicator sma9 = new SMAIndicator(closePrice, 9);
        SMAIndicator sma18 = new SMAIndicator(closePrice, 18);

        for (int i = 18; i < candles.size(); i++) {
            CandleFixture candle = candles.get(i);
            CandleFixture nextCandle = (i + 1 < candles.size()) ? candles.get(i + 1) : null;

            worker.onCandleClosed(candle.event());

            int barIndex = buffer.series().getEndIndex();
            double actualSma9 = sma9.getValue(barIndex).doubleValue();
            double actualSma18 = sma18.getValue(barIndex).doubleValue();

            assertThat(actualSma9).isCloseTo(candle.tvSma9(), org.assertj.core.data.Offset.offset(0.01));
            assertThat(actualSma18).isCloseTo(candle.tvSma18(), org.assertj.core.data.Offset.offset(0.01));

            if (nextCandle != null) {
                broker.setNextExecution(nextCandle.open(), nextCandle.time());
            }

            TickEvent closeTick = Instancio.of(TickEvent.class)
                    .set(Select.field(TickEvent.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                    .set(Select.field(TickEvent.class, "symbol"), SYMBOL)
                    .set(Select.field(TickEvent.class, "lastPrice"), candle.close())
                    .set(Select.field(TickEvent.class, "timestamp"), candle.event().closeTime().minusMillis(1))
                    .create();
            worker.onTick(closeTick);
        }

        List<OrderEntity> executedOrders = persistence.getOrders();
        assertThat(executedOrders).hasSize(3);

        List<SignalEntity> detectedSignals = persistence.getSignals();
        assertThat(detectedSignals.stream()
                .filter(s -> s.getCandleOpenTime().isBefore(Instant.parse("2026-11-13T00:00:00Z"))))
                .isEmpty();

        OrderEntity buyOpenOrder = executedOrders.get(0);
        assertThat(buyOpenOrder.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(buyOpenOrder.getAvgFillPrice()).isEqualByComparingTo("62.89");
        assertThat(broker.getOrderExecutionTimes().get(buyOpenOrder.getClientOrderId()))
                .isEqualTo(Instant.parse("2026-11-16T14:30:00Z"));

        OrderEntity buyCloseOrder = executedOrders.get(1);
        assertThat(buyCloseOrder.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(buyCloseOrder.getAvgFillPrice()).isEqualByComparingTo("59.70");

        OrderEntity sellOpenOrder = executedOrders.get(2);
        assertThat(sellOpenOrder.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(sellOpenOrder.getAvgFillPrice()).isEqualByComparingTo("59.70");
        assertThat(broker.getOrderExecutionTimes().get(sellOpenOrder.getClientOrderId()))
                .isEqualTo(Instant.parse("2026-11-18T14:30:00Z"));

        List<PositionEntity> closedPositions = persistence.getClosedPositions();
        assertThat(closedPositions).hasSize(1);
        PositionEntity firstPosition = closedPositions.get(0);
        assertThat(firstPosition.getSide()).isEqualTo(PositionSide.LONG);
        assertThat(firstPosition.getAvgEntryPrice()).isEqualByComparingTo("62.89");
        assertThat(firstPosition.getStatus()).isEqualTo(PositionStatus.CLOSED);

        BigDecimal expectedPnl = new BigDecimal("59.70").subtract(new BigDecimal("62.89"))
                .multiply(FIXED_VOLUME).setScale(2, RoundingMode.HALF_UP);
        assertThat(firstPosition.getRealizedPnl()).isEqualByComparingTo(expectedPnl);

        Optional<PositionEntity> currentPosition = persistence.loadOpenPosition(BROKER_CLIENT_ID, INSTRUMENT_PAIR_ID);
        assertThat(currentPosition).isPresent();
        assertThat(currentPosition.get().getSide()).isEqualTo(PositionSide.SHORT);
        assertThat(currentPosition.get().getAvgEntryPrice()).isEqualByComparingTo("59.70");
    }

    private List<CandleFixture> loadFixtures() {
        List<CandleFixture> fixtures = new ArrayList<>();
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] tokens = line.split(",");
                Instant time = Instant.parse(tokens[0]);
                BigDecimal open = new BigDecimal(tokens[1]);
                BigDecimal high = new BigDecimal(tokens[2]);
                BigDecimal low = new BigDecimal(tokens[3]);
                BigDecimal close = new BigDecimal(tokens[4]);
                BigDecimal volume = new BigDecimal(tokens[5]);
                double tvSma9 = Double.parseDouble(tokens[6]);
                double tvSma18 = Double.parseDouble(tokens[7]);

                Instant alignedOpenTime = time.truncatedTo(ChronoUnit.DAYS);
                Instant alignedCloseTime = alignedOpenTime.plus(Duration.ofDays(1));

                CandleEvent event = Instancio.of(CandleEvent.class)
                        .set(Select.field(CandleEvent.class, "instrumentPairId"), INSTRUMENT_PAIR_ID)
                        .set(Select.field(CandleEvent.class, "symbol"), SYMBOL)
                        .set(Select.field(CandleEvent.class, "timeframeCode"), TimeframeCode.D1)
                        .set(Select.field(CandleEvent.class, "openTime"), alignedOpenTime)
                        .set(Select.field(CandleEvent.class, "closeTime"), alignedCloseTime)
                        .set(Select.field(CandleEvent.class, "open"), open)
                        .set(Select.field(CandleEvent.class, "high"), high)
                        .set(Select.field(CandleEvent.class, "low"), low)
                        .set(Select.field(CandleEvent.class, "close"), close)
                        .set(Select.field(CandleEvent.class, "volume"), volume)
                        .create();

                fixtures.add(new CandleFixture(time, open, high, low, close, volume, tvSma9, tvSma18, event));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + FIXTURE_PATH, e);
        }
        return fixtures;
    }

    private static class InMemoryWorkerPersistenceService extends WorkerPersistenceService {
        private final Map<Long, PositionEntity> positions = new LinkedHashMap<>();
        private final List<OrderEntity> orders = new ArrayList<>();
        private final List<SignalEntity> signals = new ArrayList<>();
        private final AtomicLong idGenerator = new AtomicLong(1);

        public InMemoryWorkerPersistenceService() {
            super(null, null, null);
        }

        @Override
        public Optional<PositionEntity> loadOpenPosition(long brokerClientId, long instrumentPairId) {
            return positions.values().stream()
                    .filter(p -> p.getBrokerClientId() == brokerClientId
                            && p.getInstrumentPairId() == instrumentPairId
                            && p.getStatus() == PositionStatus.OPEN)
                    .findFirst();
        }

        @Override
        public boolean hasActiveOrders(long brokerClientId, long instrumentPairId) {
            return orders.stream()
                    .filter(o -> o.getBrokerClientId() == brokerClientId
                            && o.getInstrumentPairId() == instrumentPairId)
                    .anyMatch(o -> ACTIVE_ORDER_STATUSES.contains(o.getStatus()));
        }

        @Override
        public Optional<OrderSide> loadLatestExecutedSignalDirection(long algotradingId) {
            for (int i = signals.size() - 1; i >= 0; i--) {
                SignalEntity s = signals.get(i);
                if (s.getAlgotradingId() == algotradingId && s.getStatus() == SignalStatus.EXECUTED) {
                    return directionFor(s.getSignalType());
                }
            }
            return Optional.empty();
        }

        @Override
        public SignalEntity saveDetectedSignal(SignalEntity signal) {
            if (signal.getId() == null) {
                signal.setId(idGenerator.getAndIncrement());
            }
            signal.setStatus(SignalStatus.DETECTED);
            signals.add(signal);
            return signal;
        }

        @Override
        public SignalEntity updateSignalStatus(SignalEntity signal, SignalStatus status, String errorMessage) {
            signal.setStatus(status);
            signal.setErrorMessage(errorMessage);
            return signal;
        }

        @Override
        public OrderEntity saveOrder(OrderEntity order) {
            if (order.getId() == null) {
                order.setId(idGenerator.getAndIncrement());
            }
            orders.add(order);
            return order;
        }

        @Override
        public PositionEntity savePosition(PositionEntity position) {
            if (position.getId() == null) {
                position.setId(idGenerator.getAndIncrement());
            }
            positions.put(position.getId(), position);
            return position;
        }

        @Override
        public Optional<OrderEntity> loadStopOrder(Long positionId) {
            return Optional.empty();
        }

        public List<SignalEntity> getSignals() {
            return signals;
        }

        public List<OrderEntity> getOrders() {
            return orders;
        }

        public List<PositionEntity> getClosedPositions() {
            return positions.values().stream()
                    .filter(p -> p.getStatus() == PositionStatus.CLOSED)
                    .toList();
        }
    }

    private static class InMemoryBrokerExecutionPort implements BrokerExecutionPort {
        private final Map<String, Instant> orderExecutionTimes = new HashMap<>();
        private BigDecimal nextPrice = BigDecimal.ZERO;
        private Instant nextTime = Instant.now();
        private long orderSeq = 1;

        public void setNextExecution(BigDecimal price, Instant time) {
            this.nextPrice = price;
            this.nextTime = time;
        }

        @Override
        public BigDecimal getAvailableBalance(long brokerClientId, long instrumentPairId) {
            return new BigDecimal("10000.0");
        }

        @Override
        public OrderExecutionReceipt placeOrder(PlaceOrderCommand command) {
            String exchangeId = "EX-" + orderSeq++;
            orderExecutionTimes.put(command.clientOrderId(), nextTime);

            return Instancio.of(OrderExecutionReceipt.class)
                    .set(Select.field(OrderExecutionReceipt.class, "exchangeOrderId"), exchangeId)
                    .set(Select.field(OrderExecutionReceipt.class, "status"), OrderStatus.FILLED)
                    .set(Select.field(OrderExecutionReceipt.class, "filledQuantity"), command.quantity())
                    .set(Select.field(OrderExecutionReceipt.class, "avgFillPrice"), nextPrice)
                    .set(Select.field(OrderExecutionReceipt.class, "commission"), BigDecimal.ZERO)
                    .create();
        }

        @Override
        public void amendStopLoss(AmendStopLossCommand command) {
        }

        @Override
        public void cancelOrder(long brokerClientId, long instrumentPairId, String exchangeOrderId) {
        }

        public Map<String, Instant> getOrderExecutionTimes() {
            return orderExecutionTimes;
        }
    }
}
