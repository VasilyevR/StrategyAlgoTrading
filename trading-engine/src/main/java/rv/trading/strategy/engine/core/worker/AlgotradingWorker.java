package rv.trading.strategy.engine.core.worker;

import lombok.Getter;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.AlgotradingStatus;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.contracts.enums.TimeInForce;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;
import rv.trading.strategy.engine.core.indicator.TrailingStopIndicator;
import rv.trading.strategy.engine.core.indicator.DirectionBitMask;
import rv.trading.strategy.engine.core.port.AmendStopLossCommand;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.MarketHistoryPort;
import rv.trading.strategy.engine.core.port.PlaceOrderCommand;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import rv.trading.strategy.engine.core.signal.SignalBitMaskEvaluator;
import rv.trading.strategy.engine.core.signal.SignalEvaluationContext;
import rv.trading.strategy.engine.core.signal.SignalEvaluationResult;
import rv.trading.strategy.engine.core.signal.StrategyConfigValidator;
import rv.trading.strategy.engine.core.sizing.OrderSizingException;
import rv.trading.strategy.engine.core.sizing.PositionSizer;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class AlgotradingWorker {
    public static final String MARKET_CLIENT_ORDER_PREFIX = "AT-MARKET-";
    public static final String STOP_CLIENT_ORDER_PREFIX = "AT-STOP-";
    private static final int CLIENT_ORDER_SUFFIX_LENGTH = 24;

    private final ReentrantLock lock = new ReentrantLock();
    private Instant lastEntryBarBeginTime;

    @Getter
    private AlgotradingRuntimeConfig config;
    private BarSeriesBuffer barSeriesBuffer;
    private StrategyConfigValidator configValidator;
    private MarketHistoryPort marketHistoryPort;
    private BrokerExecutionPort brokerExecutionPort;
    private RuntimeConfigPort runtimeConfigPort;
    private WorkerPersistenceService persistenceService;
    private PositionSizer positionSizer;
    private SignalBitMaskEvaluator signalEvaluator;
    private IndicatorRegistry indicatorRegistry;
    private Optional<OrderSide> lastSignalDirection = Optional.empty();
    @Getter
    private boolean stopped;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AlgotradingWorker.class);
    private static final long TICK_LOG_INTERVAL_SECONDS = 15L;
    private Instant lastTickLogAt = Instant.EPOCH;

    public AlgotradingWorker() {
    }

    public void initialize(AlgotradingRuntimeConfig config,
                           BarSeriesBuffer barSeriesBuffer,
                           StrategyConfigValidator configValidator,
                           MarketHistoryPort marketHistoryPort,
                           BrokerExecutionPort brokerExecutionPort,
                           RuntimeConfigPort runtimeConfigPort,
                           WorkerPersistenceService persistenceService,
                           PositionSizer positionSizer,
                           SignalBitMaskEvaluator signalEvaluator,
                           IndicatorRegistry indicatorRegistry) {
        this.config = config;
        this.barSeriesBuffer = barSeriesBuffer;
        this.configValidator = configValidator;
        this.marketHistoryPort = marketHistoryPort;
        this.brokerExecutionPort = brokerExecutionPort;
        this.runtimeConfigPort = runtimeConfigPort;
        this.persistenceService = persistenceService;
        this.positionSizer = positionSizer;
        this.signalEvaluator = signalEvaluator;
        this.indicatorRegistry = indicatorRegistry;
        this.stopped = false;
    }

    public void warmUp() {
        lock.lock();
        try {
            configValidator.validate(config);
            List<CandleEvent> candles = marketHistoryPort.loadRecentCandles(
                    config.instrumentPairId(), config.timeframeCode(), BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT);
            barSeriesBuffer.initialize(candles);

            LOGGER.debug("Worker warmed up: algotradingId={}, symbol={}, tf={}, loadedCandles={}, barsInSeries={}, lastDirection={}",
                    config.algotradingId(), config.symbol(), config.timeframeCode(),
                    candles.size(), barSeriesBuffer.series().getBarCount(), lastSignalDirection.orElse(null));

            lastSignalDirection = persistenceService.loadLatestExecutedSignalDirection(config.algotradingId());
            stopped = false;
            runtimeConfigPort.reportWorkerStatus(config.algotradingId(), AlgotradingStatus.ACTIVE, null);
        } catch (RuntimeException exception) {
            stopWithError(errorMessageOf(exception));
        } finally {
            lock.unlock();
        }
    }

    public void onTick(TickEvent tick) {
        lock.lock();
        try {
            if (stopped) {
                return;
            }
            barSeriesBuffer.applyTick(tick, config.timeframeCode());
            Optional<PositionEntity> openPosition = persistenceService.loadOpenPosition(
                    config.brokerClientId(), config.instrumentPairId());
            Optional<PositionSide> openPositionSide = openPosition.map(PositionEntity::getSide);
            SignalEvaluationResult evaluation = signalEvaluator.evaluate(
                    barSeriesBuffer.series(),
                    config,
                    new SignalEvaluationContext(openPositionSide, lastSignalDirection)
            );

            Instant now = Instant.now();
            if (now.isAfter(lastTickLogAt.plusSeconds(TICK_LOG_INTERVAL_SECONDS))) {
                lastTickLogAt = now;
                LOGGER.debug("Tick heartbeat: algotradingId={}, price={}, bars={}, snapshot={}, actions={}",
                        config.algotradingId(), tick.lastPrice(), barSeriesBuffer.series().getBarCount(),
                        evaluation.indicatorSnapshot(), evaluation.actions());
            }

            if (evaluation.actions().isEmpty()) {
                if (openPosition.isEmpty() && DirectionBitMask.hasUp(evaluation.resolvedDirectionMask())) {
                    lastSignalDirection = Optional.of(OrderSide.BUY);
                } else if (openPosition.isEmpty() && DirectionBitMask.hasDown(evaluation.resolvedDirectionMask())) {
                    lastSignalDirection = Optional.of(OrderSide.SELL);
                }
                return;
            }

            Instant currentBarBegin = barSeriesBuffer.series().isEmpty()
                    ? null
                    : barSeriesBuffer.series().getLastBar().getBeginTime();
            if (openPosition.isEmpty() && currentBarBegin != null && currentBarBegin.equals(lastEntryBarBeginTime)) {
                return;
            }

            SignalEntity signal = new SignalEntity();
            signal.setAlgotradingId(config.algotradingId());
            signal.setCandleOpenTime(tick.timestamp());
            signal.setSignalType(evaluation.actions().get(0));
            signal.setTriggerPrice(tick.lastPrice());
            signal.setIndicatorSnapshot(new HashMap<>(evaluation.indicatorSnapshot()));
            signal = persistenceService.saveDetectedSignal(signal);

            try {
                for (SignalType action : evaluation.actions()) {
                    executeAction(action, tick.lastPrice(), signal);
                }
                if (signal.getStatus() == SignalStatus.IGNORED) {
                    return;
                }
                SignalType executedAction = evaluation.actions().get(evaluation.actions().size() - 1);
                lastSignalDirection = switch (executedAction) {
                    case BUY_OPEN, SELL_CLOSE -> Optional.of(OrderSide.BUY);
                    case SELL_OPEN, BUY_CLOSE -> Optional.of(OrderSide.SELL);
                };
                if (executedAction == SignalType.BUY_OPEN || executedAction == SignalType.SELL_OPEN) {
                    lastEntryBarBeginTime = currentBarBegin;
                }
                persistenceService.updateSignalStatus(signal, SignalStatus.EXECUTED, null);
            } catch (RuntimeException exception) {
                persistenceService.updateSignalStatus(signal, SignalStatus.FAILED, exception.getMessage());
                stopWithError(errorMessageOf(exception));
            }
        } catch (RuntimeException exception) {
            stopWithError(errorMessageOf(exception));
        } finally {
            lock.unlock();
        }
    }

    public void onCandleClosed(CandleEvent candle) {
        lock.lock();
        try {
            if (stopped) {
                return;
            }
            barSeriesBuffer.applyCandle(candle);

            LOGGER.debug("Candle closed: algotradingId={}, openTime={}, close={}, bars={}, lastDirection={}",
                    config.algotradingId(), candle.openTime(), candle.close(),
                    barSeriesBuffer.series().getBarCount(),
                    lastSignalDirection.orElse(null));

            if (config.exitMode() != ExitMode.TRAILING_STOP && config.exitMode() != ExitMode.HYBRID) {
                return;
            }
            Optional<PositionEntity> openPosition = persistenceService.loadOpenPosition(
                    config.brokerClientId(), config.instrumentPairId());
            if (openPosition.isEmpty()) {
                return;
            }
            PositionEntity position = openPosition.get();
            BigDecimal newStop = calculateTrailingStop(position.getSide());
            BigDecimal roundedStop = roundStop(newStop, position.getSide());
            BigDecimal currentStop = position.getCurrentStopLoss();
            BigDecimal close = candle.close();
            if (position.getSide() == PositionSide.LONG) {
                if (currentStop == null || roundedStop.compareTo(currentStop) <= 0
                        || roundedStop.compareTo(close) >= 0) {
                    return;
                }
            } else if (currentStop == null || roundedStop.compareTo(currentStop) >= 0
                    || roundedStop.compareTo(close) <= 0) {
                return;
            }

            Optional<OrderEntity> stopOrder = persistenceService.loadStopOrder(position.getId());
            String exchangeOrderId = stopOrder.map(OrderEntity::getExternalOrderId).orElse(null);
            brokerExecutionPort.amendStopLoss(new AmendStopLossCommand(
                    config.brokerClientId(), config.instrumentPairId(), config.symbol(),
                    exchangeOrderId, roundedStop));
            position.setCurrentStopLoss(roundedStop);
            persistenceService.savePosition(position);
            if (stopOrder.isPresent()) {
                OrderEntity order = stopOrder.get();
                order.setStopPrice(roundedStop);
                persistenceService.saveOrder(order);
            }
        } catch (RuntimeException exception) {
            stopWithError(exception.getMessage());
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            stopped = true;
            runtimeConfigPort.reportWorkerStatus(config.algotradingId(), AlgotradingStatus.STOPPED, null);
        } finally {
            lock.unlock();
        }
    }

    private void executeAction(SignalType action, BigDecimal triggerPrice, SignalEntity signal) {
        if (action == SignalType.BUY_CLOSE || action == SignalType.SELL_CLOSE) {
            executeClose(action, triggerPrice, signal);
        } else {
            executeOpen(action, triggerPrice, signal);
        }
    }

    private void executeClose(SignalType action, BigDecimal triggerPrice, SignalEntity signal) {
        Optional<PositionEntity> openPosition = persistenceService.loadOpenPosition(
                config.brokerClientId(), config.instrumentPairId());
        if (openPosition.isEmpty()) {
            return;
        }
        PositionEntity position = openPosition.get();
        boolean sideMatches = (action == SignalType.BUY_CLOSE && position.getSide() == PositionSide.LONG)
                || (action == SignalType.SELL_CLOSE && position.getSide() == PositionSide.SHORT);
        if (!sideMatches) {
            return;
        }

        Optional<OrderEntity> stopOrder = persistenceService.loadStopOrder(position.getId());
        if (stopOrder.isPresent() && stopOrder.get().getExternalOrderId() != null) {
            OrderEntity activeStop = stopOrder.get();
            Optional<OrderExecutionReceipt> existStopOrder = brokerExecutionPort.fetchOrderReceipt(
                    config.brokerClientId(), config.instrumentPairId(), config.symbol(),
                    activeStop.getExternalOrderId());

            if (existStopOrder.isPresent()) {
                OrderExecutionReceipt stopReceipt = existStopOrder.get();
                if (stopReceipt.status() == OrderStatus.FILLED) {
                    activeStop.setStatus(OrderStatus.FILLED);
                    if (stopReceipt.filledQuantity() != null && stopReceipt.filledQuantity().signum() > 0) {
                        activeStop.setFilledQuantity(stopReceipt.filledQuantity());
                    }
                    if (stopReceipt.avgFillPrice() != null && stopReceipt.avgFillPrice().signum() > 0) {
                        activeStop.setAvgFillPrice(stopReceipt.avgFillPrice());
                    }
                    if (stopReceipt.commission() != null) {
                        activeStop.setCommission(stopReceipt.commission());
                    }
                    if (stopReceipt.commissionAsset() != null) {
                        activeStop.setCommissionCurrency(stopReceipt.commissionAsset());
                    }
                    persistenceService.saveOrder(activeStop);

                    PositionEntity freshPosition = persistenceService.loadPositionById(position.getId())
                            .orElse(position);
                    if (freshPosition.getStatus() == PositionStatus.CLOSED) {
                        return;
                    }

                    BigDecimal closePrice = (stopReceipt.avgFillPrice() != null && stopReceipt.avgFillPrice().signum() > 0)
                            ? stopReceipt.avgFillPrice()
                            : (activeStop.getStopPrice() != null ? activeStop.getStopPrice() : triggerPrice);

                    BigDecimal entry = freshPosition.getAvgEntryPrice();
                    BigDecimal realizedPnl = freshPosition.getSide() == PositionSide.LONG
                            ? closePrice.subtract(entry).multiply(freshPosition.getQuantity())
                            : entry.subtract(closePrice).multiply(freshPosition.getQuantity());
                    BigDecimal exitFee = stopReceipt.commission() == null ? BigDecimal.ZERO : stopReceipt.commission();
                    BigDecimal prevCommission = freshPosition.getTotalCommission() == null
                            ? BigDecimal.ZERO
                            : freshPosition.getTotalCommission();

                    freshPosition.setStatus(PositionStatus.CLOSED);
                    freshPosition.setClosedAt(Instant.now());
                    freshPosition.setRealizedPnl(realizedPnl);
                    freshPosition.setTotalCommission(prevCommission.add(exitFee));
                    persistenceService.savePosition(freshPosition);
                    return;
                }
                if (stopReceipt.status() == OrderStatus.CANCELED|| stopReceipt.status() == OrderStatus.REJECTED) {
                    activeStop.setStatus(OrderStatus.CANCELED);
                    persistenceService.saveOrder(activeStop);
                } else {
                    brokerExecutionPort.cancelOrder(config.brokerClientId(), config.instrumentPairId(),
                            activeStop.getExternalOrderId());
                    activeStop.setStatus(OrderStatus.CANCELED);
                    persistenceService.saveOrder(activeStop);
                }
            } else {
                brokerExecutionPort.cancelOrder(config.brokerClientId(), config.instrumentPairId(),
                        activeStop.getExternalOrderId());
                activeStop.setStatus(OrderStatus.CANCELED);
                persistenceService.saveOrder(activeStop);
            }
        }

        OrderSide orderSide = action == SignalType.BUY_CLOSE ? OrderSide.SELL : OrderSide.BUY;
        PlaceOrderCommand command = new PlaceOrderCommand(
                config.brokerClientId(), config.instrumentPairId(), config.symbol(),
                uniqueClientOrderId(MARKET_CLIENT_ORDER_PREFIX), orderSide, OrderType.MARKET,
                TimeInForce.GTC, position.getQuantity(), null, null, true);
        OrderExecutionReceipt receipt = brokerExecutionPort.placeOrder(command);

        LOGGER.debug("Broker order result: algotradingId={}, side={}, type={}, qty={}, status={}, orderId={}, avgPrice={}, rejectReason={}",
                config.algotradingId(), command.side(), command.orderType(), command.quantity(),
                receipt.status(), receipt.exchangeOrderId(), receipt.avgFillPrice(), receipt.rejectReason());

        OrderEntity order = new OrderEntity();
        order.setBrokerClientId(config.brokerClientId());
        order.setInstrumentPairId(config.instrumentPairId());
        order.setPositionId(position.getId());
        order.setAlgotradingId(config.algotradingId());
        order.setSignalId(signal.getId());
        order.setExternalOrderId(receipt.exchangeOrderId());
        order.setClientOrderId(command.clientOrderId());
        order.setSide(orderSide);
        order.setOrderType(OrderType.MARKET);
        order.setTimeInForce(TimeInForce.GTC);
        order.setOutsideRth(false);
        order.setQuantity(position.getQuantity());
        order.setFilledQuantity(receipt.filledQuantity() == null ? BigDecimal.ZERO : receipt.filledQuantity());
        order.setAvgFillPrice(receipt.avgFillPrice());
        order.setCommission(receipt.commission() == null ? BigDecimal.ZERO : receipt.commission());
        order.setCommissionCurrency(receipt.commissionAsset());
        order.setStatus(receipt.status());
        order.setRejectReason(receipt.rejectReason());
        order = persistenceService.saveOrder(order);

        if (receipt.status() == OrderStatus.REJECTED) {
            throw new IllegalStateException("Order rejected by broker: " + receipt.rejectReason());
        }

        BigDecimal closePrice = receipt.avgFillPrice() == null ? triggerPrice : receipt.avgFillPrice();
        BigDecimal entry = position.getAvgEntryPrice();
        BigDecimal realizedPnl = position.getSide() == PositionSide.LONG
                ? closePrice.subtract(entry).multiply(position.getQuantity())
                : entry.subtract(closePrice).multiply(position.getQuantity());
        position.setStatus(PositionStatus.CLOSED);
        position.setClosedAt(Instant.now());
        position.setRealizedPnl(realizedPnl);
        position.setTotalCommission(position.getTotalCommission().add(order.getCommission()));
        persistenceService.savePosition(position);
    }

    private void executeOpen(SignalType action, BigDecimal triggerPrice, SignalEntity signal) {
        if (persistenceService.loadOpenPosition(config.brokerClientId(), config.instrumentPairId()).isPresent()
                || persistenceService.hasActiveOrders(config.brokerClientId(), config.instrumentPairId())) {
            persistenceService.updateSignalStatus(signal, SignalStatus.IGNORED, null);
            return;
        }

        PositionSide positionSide = action == SignalType.BUY_OPEN ? PositionSide.LONG : PositionSide.SHORT;
        BigDecimal quantity = positionSizer.calculateQuantity(config, barSeriesBuffer.series(), positionSide, triggerPrice);
        OrderSide orderSide = action == SignalType.BUY_OPEN ? OrderSide.BUY : OrderSide.SELL;
        PlaceOrderCommand command = new PlaceOrderCommand(
                config.brokerClientId(), config.instrumentPairId(), config.symbol(),
                uniqueClientOrderId(MARKET_CLIENT_ORDER_PREFIX), orderSide, OrderType.MARKET,
                TimeInForce.GTC, quantity, null, null, false);
        OrderExecutionReceipt receipt = brokerExecutionPort.placeOrder(command);

        LOGGER.debug("Broker order result: algotradingId={}, side={}, type={}, qty={}, status={}, orderId={}, avgPrice={}, rejectReason={}",
                config.algotradingId(), command.side(), command.orderType(), command.quantity(),
                receipt.status(), receipt.exchangeOrderId(), receipt.avgFillPrice(), receipt.rejectReason());

        if (receipt.status() == OrderStatus.REJECTED) {
            OrderEntity rejectedOrder = new OrderEntity();
            rejectedOrder.setBrokerClientId(config.brokerClientId());
            rejectedOrder.setInstrumentPairId(config.instrumentPairId());
            rejectedOrder.setAlgotradingId(config.algotradingId());
            rejectedOrder.setSignalId(signal.getId());
            rejectedOrder.setExternalOrderId(receipt.exchangeOrderId());
            rejectedOrder.setClientOrderId(command.clientOrderId());
            rejectedOrder.setSide(orderSide);
            rejectedOrder.setOrderType(OrderType.MARKET);
            rejectedOrder.setTimeInForce(TimeInForce.GTC);
            rejectedOrder.setOutsideRth(false);
            rejectedOrder.setQuantity(quantity);
            rejectedOrder.setFilledQuantity(BigDecimal.ZERO);
            rejectedOrder.setCommission(BigDecimal.ZERO);
            rejectedOrder.setStatus(OrderStatus.REJECTED);
            rejectedOrder.setRejectReason(receipt.rejectReason());
            persistenceService.saveOrder(rejectedOrder);
            throw new IllegalStateException("Order rejected by broker: " + receipt.rejectReason());
        }

        PositionEntity position = new PositionEntity();
        position.setBrokerClientId(config.brokerClientId());
        position.setInstrumentPairId(config.instrumentPairId());
        position.setSide(positionSide);
        position.setStatus(PositionStatus.OPEN);
        position.setQuantity(receipt.filledQuantity() == null ? quantity : receipt.filledQuantity());
        position.setAvgEntryPrice(receipt.avgFillPrice() == null ? triggerPrice : receipt.avgFillPrice());
        position.setRealizedPnl(BigDecimal.ZERO);
        position.setTotalCommission(receipt.commission() == null ? BigDecimal.ZERO : receipt.commission());
        position.setCurrency(config.symbol());
        position = persistenceService.savePosition(position);

        OrderEntity marketOrder = new OrderEntity();
        marketOrder.setBrokerClientId(config.brokerClientId());
        marketOrder.setInstrumentPairId(config.instrumentPairId());
        marketOrder.setPositionId(position.getId());
        marketOrder.setAlgotradingId(config.algotradingId());
        marketOrder.setSignalId(signal.getId());
        marketOrder.setExternalOrderId(receipt.exchangeOrderId());
        marketOrder.setClientOrderId(command.clientOrderId());
        marketOrder.setSide(orderSide);
        marketOrder.setOrderType(OrderType.MARKET);
        marketOrder.setTimeInForce(TimeInForce.GTC);
        marketOrder.setOutsideRth(false);
        marketOrder.setQuantity(quantity);
        marketOrder.setFilledQuantity(receipt.filledQuantity() == null ? BigDecimal.ZERO : receipt.filledQuantity());
        marketOrder.setAvgFillPrice(receipt.avgFillPrice());
        marketOrder.setCommission(receipt.commission() == null ? BigDecimal.ZERO : receipt.commission());
        marketOrder.setCommissionCurrency(receipt.commissionAsset());
        marketOrder.setStatus(receipt.status());
        marketOrder.setRejectReason(receipt.rejectReason());
        persistenceService.saveOrder(marketOrder);

        if (config.exitMode() == ExitMode.TRAILING_STOP || config.exitMode() == ExitMode.HYBRID) {
            BigDecimal stop = calculateTrailingStop(positionSide);
            BigDecimal roundedStop = roundStop(stop, positionSide);
            BigDecimal entryPrice = position.getAvgEntryPrice();
            if (positionSide == PositionSide.LONG && roundedStop.compareTo(entryPrice) >= 0) {
                throw new OrderSizingException(PositionSizer.INVALID_STOP_MESSAGE);
            }
            if (positionSide == PositionSide.SHORT && roundedStop.compareTo(entryPrice) <= 0) {
                throw new OrderSizingException(PositionSizer.INVALID_STOP_MESSAGE);
            }
            position.setCurrentStopLoss(roundedStop);
            persistenceService.savePosition(position);

            PlaceOrderCommand stopCommand = new PlaceOrderCommand(
                    config.brokerClientId(), config.instrumentPairId(), config.symbol(),
                    uniqueClientOrderId(STOP_CLIENT_ORDER_PREFIX),
                    positionSide == PositionSide.LONG ? OrderSide.SELL : OrderSide.BUY,
                    OrderType.STOP, TimeInForce.GTC, position.getQuantity(), null, roundedStop, true);
            OrderExecutionReceipt stopReceipt = brokerExecutionPort.placeOrder(stopCommand);

            OrderEntity stopOrder = new OrderEntity();
            stopOrder.setBrokerClientId(config.brokerClientId());
            stopOrder.setInstrumentPairId(config.instrumentPairId());
            stopOrder.setPositionId(position.getId());
            stopOrder.setAlgotradingId(config.algotradingId());
            stopOrder.setSignalId(signal.getId());
            stopOrder.setExternalOrderId(stopReceipt.exchangeOrderId());
            stopOrder.setClientOrderId(stopCommand.clientOrderId());
            stopOrder.setSide(stopCommand.side());
            stopOrder.setOrderType(OrderType.STOP);
            stopOrder.setTimeInForce(TimeInForce.GTC);
            stopOrder.setOutsideRth(false);
            stopOrder.setQuantity(position.getQuantity());
            stopOrder.setFilledQuantity(stopReceipt.filledQuantity() == null ? BigDecimal.ZERO : stopReceipt.filledQuantity());
            stopOrder.setStopPrice(roundedStop);
            stopOrder.setAvgFillPrice(stopReceipt.avgFillPrice());
            stopOrder.setCommission(stopReceipt.commission() == null ? BigDecimal.ZERO : stopReceipt.commission());
            stopOrder.setCommissionCurrency(stopReceipt.commissionAsset());
            stopOrder.setStatus(stopReceipt.status());
            stopOrder.setRejectReason(stopReceipt.rejectReason());
            persistenceService.saveOrder(stopOrder);
        }
    }

    private BigDecimal calculateTrailingStop(PositionSide side) {
        StrategyIndicatorConfig stopConfig = config.indicators().stream()
                .filter(indicator -> indicator.role() == IndicatorRole.TRAILING_STOP)
                .findFirst()
                .orElseThrow(() -> new OrderSizingException(PositionSizer.INVALID_STOP_MESSAGE));
        TrailingStopIndicator indicator = indicatorRegistry
                .getTrailingStopIndicator(stopConfig.indicatorCode().name())
                .orElseThrow(() -> new OrderSizingException(PositionSizer.INVALID_STOP_MESSAGE));
        return indicator.calculateStopLoss(barSeriesBuffer.series(), side, stopConfig.params())
                .orElseThrow(() -> new OrderSizingException(PositionSizer.INVALID_STOP_MESSAGE));
    }

    private BigDecimal roundStop(BigDecimal stop, PositionSide side) {
        RoundingMode rounding = side == PositionSide.LONG ? RoundingMode.DOWN : RoundingMode.UP;
        BigDecimal units = stop.divide(config.tickSize(), 0, rounding);
        return units.multiply(config.tickSize());
    }

    private void stopWithError(String errorMessage) {
        stopped = true;
        runtimeConfigPort.reportWorkerStatus(config.algotradingId(), AlgotradingStatus.ERROR, errorMessage);
    }

    private static String uniqueClientOrderId(String prefix) {
        String compactUuid = UUID.randomUUID().toString().replace("-", "")
                .substring(0, CLIENT_ORDER_SUFFIX_LENGTH);
        return prefix + compactUuid;
    }

    private static String errorMessageOf(RuntimeException exception) {
        String message = exception.getMessage();
        return (message == null || message.isBlank())
                ? exception.getClass().getSimpleName()
                : message;
    }
}
