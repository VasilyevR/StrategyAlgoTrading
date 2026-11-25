package rv.trading.strategy.engine.core.worker;

import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.params.provider.Arguments;

@ExtendWith(MockitoExtension.class)
class WorkerPersistenceServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SignalRepository signalRepository;

    private WorkerPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new WorkerPersistenceService(positionRepository, orderRepository, signalRepository);
    }

    @Test
    void loadsAccountWideOpenPositionByBrokerAndPair() {
        PositionEntity position = Instancio.create(PositionEntity.class);
        when(positionRepository.findFirstByBrokerClientIdAndInstrumentPairIdAndStatus(
                11L, 19L, PositionStatus.OPEN)).thenReturn(Optional.of(position));

        assertThat(service.loadOpenPosition(11L, 19L)).containsSame(position);

        verify(positionRepository).findFirstByBrokerClientIdAndInstrumentPairIdAndStatus(
                11L, 19L, PositionStatus.OPEN);
    }

    @Test
    void checksAllInFlightOrderStatusesAccountWide() {
        when(orderRepository.existsByBrokerClientIdAndInstrumentPairIdAndStatusIn(
                11L, 19L, WorkerPersistenceService.ACTIVE_ORDER_STATUSES)).thenReturn(true);

        assertThat(service.hasActiveOrders(11L, 19L)).isTrue();
        assertThat(WorkerPersistenceService.ACTIVE_ORDER_STATUSES).containsExactly(
                OrderStatus.PENDING_SUBMIT,
                OrderStatus.PRE_SUBMITTED,
                OrderStatus.OPEN,
                OrderStatus.PARTIALLY_FILLED
        );

        verify(orderRepository).existsByBrokerClientIdAndInstrumentPairIdAndStatusIn(
                11L, 19L, WorkerPersistenceService.ACTIVE_ORDER_STATUSES);
    }

    @ParameterizedTest
    @MethodSource("signalDirections")
    void resolvesLatestExecutedSignalToItsMarketDirection(SignalType signalType, OrderSide expectedDirection) {
        SignalEntity signal = Instancio.create(SignalEntity.class);
        signal.setSignalType(signalType);
        when(signalRepository.findFirstByAlgotradingIdAndStatusOrderByCreatedAtDesc(
                31L, SignalStatus.EXECUTED)).thenReturn(Optional.of(signal));

        assertThat(service.loadLatestExecutedSignalDirection(31L)).contains(expectedDirection);

        verify(signalRepository).findFirstByAlgotradingIdAndStatusOrderByCreatedAtDesc(
                31L, SignalStatus.EXECUTED);
    }

    private static Stream<Arguments> signalDirections() {
        return Stream.of(
                Arguments.of(SignalType.BUY_OPEN, OrderSide.BUY),
                Arguments.of(SignalType.SELL_OPEN, OrderSide.SELL),
                Arguments.of(SignalType.BUY_CLOSE, OrderSide.SELL),
                Arguments.of(SignalType.SELL_CLOSE, OrderSide.BUY)
        );
    }

    @Test
    void reportsEmptyDirectionWhenThereIsNoExecutedSignal() {
        when(signalRepository.findFirstByAlgotradingIdAndStatusOrderByCreatedAtDesc(
                31L, SignalStatus.EXECUTED)).thenReturn(Optional.empty());

        assertThat(service.loadLatestExecutedSignalDirection(31L)).isEmpty();
    }

    @Test
    void savesAndUpdatesSignalsWithRequestedStatusAndError() {
        SignalEntity signal = Instancio.create(SignalEntity.class);
        when(signalRepository.save(signal)).thenReturn(signal);

        assertThat(service.saveDetectedSignal(signal)).isSameAs(signal);
        assertThat(signal.getStatus()).isEqualTo(SignalStatus.DETECTED);

        assertThat(service.updateSignalStatus(signal, SignalStatus.FAILED, "worker failure")).isSameAs(signal);
        assertThat(signal.getStatus()).isEqualTo(SignalStatus.FAILED);
        assertThat(signal.getErrorMessage()).isEqualTo("worker failure");
        verify(signalRepository, org.mockito.Mockito.times(2)).save(signal);
    }

    @Test
    void loadsStopOrdersOnlyForOpenStatusesAndAvoidsQueryForMissingPositionId() {
        OrderEntity stop = Instancio.create(OrderEntity.class);
        when(orderRepository.findFirstByPositionIdAndOrderTypeAndStatusIn(
                41L, OrderType.STOP, WorkerPersistenceService.ACTIVE_ORDER_STATUSES))
                .thenReturn(Optional.of(stop));

        assertThat(service.loadStopOrder(41L)).containsSame(stop);
        assertThat(service.loadStopOrder(null)).isEmpty();

        verify(orderRepository).findFirstByPositionIdAndOrderTypeAndStatusIn(
                41L, OrderType.STOP, WorkerPersistenceService.ACTIVE_ORDER_STATUSES);
    }

    @Test
    void savesOrderAndPositionEntities() {
        OrderEntity order = Instancio.create(OrderEntity.class);
        PositionEntity position = Instancio.create(PositionEntity.class);
        when(orderRepository.save(order)).thenReturn(order);
        when(positionRepository.save(position)).thenReturn(position);

        assertThat(service.saveOrder(order)).isSameAs(order);
        assertThat(service.savePosition(position)).isSameAs(position);

        verify(orderRepository).save(order);
        verify(positionRepository).save(position);
    }
}
