package rv.trading.strategy.collector.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "candle")
public class CandleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_pair_id", nullable = false)
    private Long instrumentPairId;

    @Column(name = "timeframe_id", nullable = false)
    private Long timeframeId;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "open", nullable = false, precision = 20, scale = 8)
    private BigDecimal open;

    @Column(name = "high", nullable = false, precision = 20, scale = 8)
    private BigDecimal high;

    @Column(name = "low", nullable = false, precision = 20, scale = 8)
    private BigDecimal low;

    @Column(name = "close", nullable = false, precision = 20, scale = 8)
    private BigDecimal close;

    @Column(name = "volume", nullable = false, precision = 28, scale = 8)
    private BigDecimal volume;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Transient
    private String symbol;

    @Transient
    private TimeframeCode timeframeCode;

    @Transient
    private Instant closeTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getInstrumentPairId() {
        return instrumentPairId;
    }

    public void setInstrumentPairId(Long instrumentPairId) {
        this.instrumentPairId = instrumentPairId;
    }

    public Long getTimeframeId() {
        return timeframeId;
    }

    public void setTimeframeId(Long timeframeId) {
        this.timeframeId = timeframeId;
    }

    public Instant getOpenTime() {
        return openTime;
    }

    public void setOpenTime(Instant openTime) {
        this.openTime = openTime;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public TimeframeCode getTimeframeCode() {
        return timeframeCode;
    }

    public void setTimeframeCode(TimeframeCode timeframeCode) {
        this.timeframeCode = timeframeCode;
    }

    public Instant getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(Instant closeTime) {
        this.closeTime = closeTime;
    }
}
