package rv.trading.strategy.engine.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.TimeInForce;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_client_id", nullable = false)
    private Long brokerClientId;

    @Column(name = "instrument_pair_id", nullable = false)
    private Long instrumentPairId;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "algotrading_id")
    private Long algotradingId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "parent_order_id")
    private Long parentOrderId;

    @Column(name = "external_order_id", length = 64)
    private String externalOrderId;

    @Column(name = "client_order_id", nullable = false, unique = true, length = 64)
    private String clientOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 16)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 16)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, length = 16)
    private TimeInForce timeInForce;

    @Column(name = "outside_rth", nullable = false)
    private boolean outsideRth;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "filled_quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Column(name = "price", precision = 20, scale = 8)
    private BigDecimal price;

    @Column(name = "stop_price", precision = 20, scale = 8)
    private BigDecimal stopPrice;

    @Column(name = "avg_fill_price", precision = 20, scale = 8)
    private BigDecimal avgFillPrice;

    @Column(name = "commission", nullable = false, precision = 20, scale = 8)
    private BigDecimal commission = BigDecimal.ZERO;

    @Column(name = "commission_currency", length = 16)
    private String commissionCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "reject_reason", columnDefinition = "text")
    private String rejectReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
