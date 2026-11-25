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
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "position")
@Getter
@Setter
@NoArgsConstructor
public class PositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broker_client_id", nullable = false)
    private Long brokerClientId;

    @Column(name = "instrument_pair_id", nullable = false)
    private Long instrumentPairId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 16)
    private PositionSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PositionStatus status;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_entry_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal avgEntryPrice;

    @Column(name = "current_stop_loss", precision = 20, scale = 8)
    private BigDecimal currentStopLoss;

    @Column(name = "realized_pnl", nullable = false, precision = 20, scale = 8)
    private BigDecimal realizedPnl = BigDecimal.ZERO;

    @Column(name = "total_commission", nullable = false, precision = 20, scale = 8)
    private BigDecimal totalCommission = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 16)
    private String currency;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
