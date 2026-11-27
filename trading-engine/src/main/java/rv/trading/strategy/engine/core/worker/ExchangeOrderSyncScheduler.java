package rv.trading.strategy.engine.core.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.engine.core.port.BrokerExecutionPort;
import rv.trading.strategy.engine.core.port.OrderExecutionReceipt;
import rv.trading.strategy.engine.core.port.RuntimeConfigPort;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
public class ExchangeOrderSyncScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeOrderSyncScheduler.class);

    private final WorkerPersistenceService persistenceService;
    private final BrokerExecutionPort brokerExecutionPort;
    private final RuntimeConfigPort runtimeConfigPort;

    public ExchangeOrderSyncScheduler(WorkerPersistenceService persistenceService,
                                      BrokerExecutionPort brokerExecutionPort,
                                      RuntimeConfigPort runtimeConfigPort) {
        this.persistenceService = persistenceService;
        this.brokerExecutionPort = brokerExecutionPort;
        this.runtimeConfigPort = runtimeConfigPort;
    }

    @Scheduled(fixedDelayString = "${trading-engine.order-sync-interval-millis:5000}")
    public void syncActiveOrders() {
        for (OrderEntity order : persistenceService.loadActiveOrders()) {
            if (order.getExternalOrderId() == null || order.getExternalOrderId().isBlank()) {
                continue;
            }
            String symbol = resolveSymbol(order);
            if (symbol == null) {
                continue;
            }
            try {
                Optional<OrderExecutionReceipt> receiptOpt = brokerExecutionPort.fetchOrderReceipt(
                        order.getBrokerClientId(), order.getInstrumentPairId(),
                        symbol, order.getExternalOrderId());
                if (receiptOpt.isEmpty()) {
                    continue;
                }
                applyReceipt(order, receiptOpt.get());
            } catch (RuntimeException exception) {
                LOGGER.debug("Order sync skipped for orderId={}: {}",
                        order.getExternalOrderId(), exception.getMessage());
            }
        }
    }

    private void applyReceipt(OrderEntity order, OrderExecutionReceipt receipt) {
        if (receipt.status() == OrderStatus.OPEN || receipt.status() == order.getStatus()) {
            return;
        }
        order.setStatus(receipt.status());
        if (receipt.filledQuantity() != null && receipt.filledQuantity().signum() > 0) {
            order.setFilledQuantity(receipt.filledQuantity());
        }
        if (receipt.avgFillPrice() != null && receipt.avgFillPrice().signum() > 0) {
            order.setAvgFillPrice(receipt.avgFillPrice());
        }
        if (receipt.commission() != null) {
            order.setCommission(receipt.commission());
        }
        if (receipt.commissionAsset() != null) {
            order.setCommissionCurrency(receipt.commissionAsset());
        }
        if (receipt.rejectReason() != null) {
            order.setRejectReason(receipt.rejectReason());
        }
        persistenceService.saveOrder(order);

        if (receipt.status() == OrderStatus.FILLED && order.getOrderType() == OrderType.STOP) {
            closePositionFromStop(order, receipt);
        }
    }

    private void closePositionFromStop(OrderEntity stopOrder, OrderExecutionReceipt receipt) {
        Optional<PositionEntity> positionOpt = persistenceService.loadPositionById(stopOrder.getPositionId());
        if (positionOpt.isEmpty() || positionOpt.get().getStatus() != PositionStatus.OPEN) {
            return;
        }
        PositionEntity position = positionOpt.get();
        BigDecimal exitPrice = receipt.avgFillPrice() != null && receipt.avgFillPrice().signum() > 0
                ? receipt.avgFillPrice()
                : stopOrder.getStopPrice();
        BigDecimal realizedPnl = BigDecimal.ZERO;
        if (exitPrice != null && position.getAvgEntryPrice() != null) {
            realizedPnl = position.getSide() == PositionSide.LONG
                    ? exitPrice.subtract(position.getAvgEntryPrice()).multiply(position.getQuantity())
                    : position.getAvgEntryPrice().subtract(exitPrice).multiply(position.getQuantity());
        }
        BigDecimal fee = receipt.commission() == null ? BigDecimal.ZERO : receipt.commission();
        position.setStatus(PositionStatus.CLOSED);
        position.setClosedAt(Instant.now());
        position.setRealizedPnl(realizedPnl);
        BigDecimal prevCommission = position.getTotalCommission() == null
                ? BigDecimal.ZERO
                : position.getTotalCommission();
        position.setTotalCommission(prevCommission.add(fee));
        persistenceService.savePosition(position);
        LOGGER.info("Position closed by exchange stop-loss: positionId={}, side={}, exitPrice={}, realizedPnl={}",
                position.getId(), position.getSide(), exitPrice, realizedPnl);
    }

    private String resolveSymbol(OrderEntity order) {
        return runtimeConfigPort.loadActiveConfigs().stream()
                .filter(cfg -> cfg.instrumentPairId() == order.getInstrumentPairId())
                .map(AlgotradingRuntimeConfig::symbol)
                .findFirst()
                .orElseGet(() -> persistenceService.loadPositionById(order.getPositionId())
                        .map(PositionEntity::getCurrency)
                        .orElse(null));
    }
}
