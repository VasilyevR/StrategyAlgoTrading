package rv.trading.strategy.engine;

import org.instancio.Instancio;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.contracts.enums.TimeInForce;
import rv.trading.strategy.engine.persistence.entity.OrderEntity;
import rv.trading.strategy.engine.persistence.entity.PositionEntity;
import rv.trading.strategy.engine.persistence.entity.SignalEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static OrderEntity order(long id) {
        OrderEntity entity = Instancio.create(OrderEntity.class);
        entity.setId(id);
        entity.setBrokerClientId(101L);
        entity.setInstrumentPairId(201L);
        entity.setClientOrderId("client-order-" + id);
        entity.setSide(OrderSide.BUY);
        entity.setOrderType(OrderType.LIMIT);
        entity.setTimeInForce(TimeInForce.GTC);
        entity.setQuantity(new BigDecimal("2.50000000"));
        entity.setFilledQuantity(new BigDecimal("1.25000000"));
        entity.setCommission(new BigDecimal("0.12500000"));
        entity.setStatus(OrderStatus.PARTIALLY_FILLED);
        entity.setVersion(3L);
        entity.setCreatedAt(Instant.parse("2025-01-01T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2025-01-01T10:01:00Z"));
        return entity;
    }

    public static PositionEntity position(long id) {
        PositionEntity entity = Instancio.create(PositionEntity.class);
        entity.setId(id);
        entity.setBrokerClientId(101L);
        entity.setInstrumentPairId(201L);
        entity.setSide(PositionSide.LONG);
        entity.setStatus(PositionStatus.OPEN);
        entity.setQuantity(new BigDecimal("2.50000000"));
        entity.setAvgEntryPrice(new BigDecimal("100.25000000"));
        entity.setCurrentStopLoss(new BigDecimal("95.00000000"));
        entity.setRealizedPnl(new BigDecimal("4.25000000"));
        entity.setTotalCommission(new BigDecimal("0.12500000"));
        entity.setCurrency("USD");
        entity.setVersion(2L);
        entity.setOpenedAt(Instant.parse("2025-01-01T10:00:00Z"));
        entity.setClosedAt(null);
        return entity;
    }

    public static SignalEntity signal(long id) {
        SignalEntity entity = Instancio.create(SignalEntity.class);
        entity.setId(id);
        entity.setAlgotradingId(301L);
        entity.setCandleOpenTime(Instant.parse("2025-01-01T10:00:00Z"));
        entity.setSignalType(SignalType.BUY_OPEN);
        entity.setStatus(SignalStatus.DETECTED);
        entity.setTriggerPrice(new BigDecimal("100.25000000"));
        entity.setIndicatorSnapshot(Map.of("rsi", new BigDecimal("42.5"), "trend", "up"));
        entity.setErrorMessage(null);
        entity.setCreatedAt(Instant.parse("2025-01-01T10:01:00Z"));
        return entity;
    }
}
