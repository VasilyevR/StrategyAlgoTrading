package rv.trading.strategy.engine.core.worker;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;
import rv.trading.strategy.engine.persistence.repository.OrderRepository;
import rv.trading.strategy.engine.persistence.repository.PositionRepository;
import rv.trading.strategy.engine.persistence.repository.SignalRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

@Service
public class WorkerPersistenceService {
    public static final Collection<OrderStatus> ACTIVE_ORDER_STATUSES = java.util.List.of(
            OrderStatus.PENDING_SUBMIT,
            OrderStatus.PRE_SUBMITTED,
            OrderStatus.OPEN,
            OrderStatus.PARTIALLY_FILLED
    );

    private final PositionRepository positionRepository;
    private final OrderRepository orderRepository;
    private final SignalRepository signalRepository;

    public WorkerPersistenceService(PositionRepository positionRepository,
                                     OrderRepository orderRepository,
                                     SignalRepository signalRepository) {
        this.positionRepository = positionRepository;
        this.orderRepository = orderRepository;
        this.signalRepository = signalRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PositionEntity> loadOpenPosition(long brokerClientId, long instrumentPairId) {
        return positionRepository.findFirstByBrokerClientIdAndInstrumentPairIdAndStatus(
                brokerClientId, instrumentPairId, PositionStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOrders(long brokerClientId, long instrumentPairId) {
        return orderRepository.existsByBrokerClientIdAndInstrumentPairIdAndStatusIn(
                brokerClientId, instrumentPairId, ACTIVE_ORDER_STATUSES);
    }

    @Transactional(readOnly = true)
    public Optional<OrderSide> loadLatestExecutedSignalDirection(long algotradingId) {
        return signalRepository.findFirstByAlgotradingIdAndStatusOrderByCreatedAtDesc(
                        algotradingId, SignalStatus.EXECUTED)
                .map(SignalEntity::getSignalType)
                .flatMap(WorkerPersistenceService::directionFor);
    }

    @Transactional
    public SignalEntity saveDetectedSignal(SignalEntity signal) {
        signal.setStatus(SignalStatus.DETECTED);
        return signalRepository.save(signal);
    }

    @Transactional
    public SignalEntity updateSignalStatus(SignalEntity signal, SignalStatus status, String errorMessage) {
        signal.setStatus(status);
        signal.setErrorMessage(errorMessage);
        return signalRepository.save(signal);
    }

    @Transactional
    public OrderEntity saveOrder(OrderEntity order) {
        return orderRepository.save(order);
    }

    @Transactional
    public PositionEntity savePosition(PositionEntity position) {
        return positionRepository.save(position);
    }

    @Transactional(readOnly = true)
    public Optional<OrderEntity> loadStopOrder(Long positionId) {
        if (positionId == null) {
            return Optional.empty();
        }
        return orderRepository.findFirstByPositionIdAndOrderTypeAndStatusIn(
                positionId, OrderType.STOP, ACTIVE_ORDER_STATUSES);
    }

    public static Optional<OrderSide> directionFor(SignalType signalType) {
        if (signalType == SignalType.BUY_OPEN || signalType == SignalType.BUY_CLOSE) {
            return Optional.of(OrderSide.BUY);
        }
        if (signalType == SignalType.SELL_OPEN || signalType == SignalType.SELL_CLOSE) {
            return Optional.of(OrderSide.SELL);
        }
        return Optional.empty();
    }
}
