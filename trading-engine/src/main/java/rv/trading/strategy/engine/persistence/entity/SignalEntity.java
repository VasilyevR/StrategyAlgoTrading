package rv.trading.strategy.engine.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "signal")
@Getter
@Setter
@NoArgsConstructor
public class SignalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "algotrading_id", nullable = false)
    private Long algotradingId;

    @Column(name = "candle_open_time", nullable = false)
    private Instant candleOpenTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 24)
    private SignalType signalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SignalStatus status;

    @Column(name = "trigger_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal triggerPrice;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "indicator_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> indicatorSnapshot = new HashMap<>();

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
