package rv.trading.strategy.engine.core.worker;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.contracts.enums.TimeframeCode;
import org.instancio.Instancio;
import org.instancio.Select;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BarSeriesBufferTest {

    private static final String PRICE_OPEN = "open";
    private static final String PRICE_HIGH = "high";
    private static final String PRICE_LOW = "low";
    private static final String PRICE_CLOSE = "close";
    private static final String PRICE_VOLUME = "volume";

    @Test
    void initializeResetsSeriesAndRetainsOnlyMaximumBarCount() {
        BarSeriesBuffer buffer = new BarSeriesBuffer();
        List<CandleEvent> candles = new ArrayList<>();
        Instant firstOpen = Instant.parse("2025-01-01T00:00:00Z");
        for (int index = 0; index <= BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT; index++) {
            Instant openTime = firstOpen.plusSeconds(index * 60L);
            candles.add(candle(openTime, openTime.plusSeconds(60),
                    BigDecimal.valueOf(index + 1L), BigDecimal.valueOf(index + 2L),
                    BigDecimal.valueOf(index), BigDecimal.valueOf(index + 1L), BigDecimal.ONE, true));
        }

        buffer.initialize(candles);

        assertThat(buffer.series().getBarCount()).isEqualTo(BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT);
        assertThat(buffer.series().getFirstBar().getBeginTime())
                .isEqualTo(firstOpen.plusSeconds(60));
        assertThat(buffer.series().getLastBar().getBeginTime())
                .isEqualTo(firstOpen.plusSeconds(BarSeriesBuffer.DEFAULT_MAXIMUM_BAR_COUNT * 60L));

        buffer.initialize(List.of(candle(firstOpen, firstOpen.plusSeconds(60),
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.valueOf(5), BigDecimal.ONE, true)));

        assertThat(buffer.series().getBarCount()).isEqualTo(1);
        assertThat(buffer.series().getFirstBar().getBeginTime()).isEqualTo(firstOpen);
    }

    @Test
    void applyTickUpdatesOhlcInsideM1WindowAndAppendsAcrossWindow() {
        BarSeriesBuffer buffer = new BarSeriesBuffer();
        Instant firstTickTime = Instant.parse("2025-01-01T10:00:10Z");
        buffer.applyTick(tick(firstTickTime, BigDecimal.valueOf(100)), TimeframeCode.M1);
        Bar initialBar = buffer.series().getLastBar();

        buffer.applyTick(tick(firstTickTime.plusSeconds(30), BigDecimal.valueOf(105)), TimeframeCode.M1);
        Bar updatedBar = buffer.series().getLastBar();

        assertThat(buffer.series().getBarCount()).isEqualTo(1);
        assertThat(updatedBar.getBeginTime()).isEqualTo(initialBar.getBeginTime());
        assertThat(updatedBar.getOpenPrice().doubleValue()).isEqualTo(100.0);
        assertThat(updatedBar.getHighPrice().doubleValue()).isEqualTo(105.0);
        assertThat(updatedBar.getLowPrice().doubleValue()).isEqualTo(100.0);
        assertThat(updatedBar.getClosePrice().doubleValue()).isEqualTo(105.0);

        buffer.applyTick(tick(firstTickTime.plusSeconds(60), BigDecimal.valueOf(98)), TimeframeCode.M1);

        assertThat(buffer.series().getBarCount()).isEqualTo(2);
        assertThat(buffer.series().getLastBar().getBeginTime()).isEqualTo(firstTickTime.plusSeconds(50));
    }

    @Test
    void applyTickAlignsHourlyBarsAndStartsNewBarAtNextHour() {
        BarSeriesBuffer buffer = new BarSeriesBuffer();
        Instant firstTickTime = Instant.parse("2025-01-01T10:05:00Z");
        buffer.applyTick(tick(firstTickTime, BigDecimal.valueOf(20)), TimeframeCode.H1);
        buffer.applyTick(tick(Instant.parse("2025-01-01T10:55:00Z"), BigDecimal.valueOf(22)), TimeframeCode.H1);

        assertThat(buffer.series().getBarCount()).isEqualTo(1);
        assertThat(buffer.series().getLastBar().getBeginTime()).isEqualTo(Instant.parse("2025-01-01T10:00:00Z"));
        assertThat(buffer.series().getLastBar().getHighPrice().doubleValue()).isEqualTo(22.0);

        buffer.applyTick(tick(Instant.parse("2025-01-01T11:00:00Z"), BigDecimal.valueOf(19)), TimeframeCode.H1);

        assertThat(buffer.series().getBarCount()).isEqualTo(2);
        assertThat(buffer.series().getLastBar().getBeginTime()).isEqualTo(Instant.parse("2025-01-01T11:00:00Z"));
    }

    @Test
    void applyCandleReplacesAnExistingBarAndAppendsNextBar() {
        BarSeriesBuffer buffer = new BarSeriesBuffer();
        Instant openTime = Instant.parse("2025-01-01T10:00:00Z");
        buffer.applyCandle(candle(openTime, openTime.plusMillis(59_999),
                BigDecimal.valueOf(10), BigDecimal.valueOf(12), BigDecimal.valueOf(9),
                BigDecimal.valueOf(11), BigDecimal.ONE, false));

        buffer.applyCandle(candle(openTime, openTime.plusMillis(59_999),
                BigDecimal.valueOf(10), BigDecimal.valueOf(15), BigDecimal.valueOf(8),
                BigDecimal.valueOf(14), BigDecimal.TEN, true));

        assertThat(buffer.series().getBarCount()).isEqualTo(1);
        assertThat(buffer.series().getLastBar().getHighPrice().doubleValue()).isEqualTo(15.0);
        assertThat(buffer.series().getLastBar().getClosePrice().doubleValue()).isEqualTo(14.0);
        assertThat(buffer.series().getLastBar().getEndTime()).isEqualTo(openTime.plusSeconds(60));
        assertThat(Duration.between(buffer.series().getLastBar().getBeginTime(),
                buffer.series().getLastBar().getEndTime())).isEqualTo(Duration.ofMinutes(1));

        Instant nextOpenTime = openTime.plusSeconds(60);
        buffer.applyCandle(candle(nextOpenTime, nextOpenTime.plusSeconds(60),
                BigDecimal.valueOf(14), BigDecimal.valueOf(16), BigDecimal.valueOf(13),
                BigDecimal.valueOf(15), BigDecimal.ONE, true));

        assertThat(buffer.series().getBarCount()).isEqualTo(2);
        assertThat(buffer.series().getLastBar().getBeginTime()).isEqualTo(nextOpenTime);
    }

    private static TickEvent tick(Instant timestamp, BigDecimal price) {
        return Instancio.of(TickEvent.class)
                .set(Select.field(TickEvent.class, "instrumentPairId"), 1L)
                .set(Select.field(TickEvent.class, "lastPrice"), price)
                .set(Select.field(TickEvent.class, "timestamp"), timestamp)
                .create();
    }

    private static CandleEvent candle(Instant openTime, Instant closeTime,
                                      BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                                      BigDecimal volume, boolean closed) {
        return Instancio.of(CandleEvent.class)
                .set(Select.field(CandleEvent.class, "instrumentPairId"), 1L)
                .set(Select.field(CandleEvent.class, "timeframeCode"), TimeframeCode.M1)
                .set(Select.field(CandleEvent.class, "openTime"), openTime)
                .set(Select.field(CandleEvent.class, "closeTime"), closeTime)
                .set(Select.field(CandleEvent.class, PRICE_OPEN), open)
                .set(Select.field(CandleEvent.class, PRICE_HIGH), high)
                .set(Select.field(CandleEvent.class, PRICE_LOW), low)
                .set(Select.field(CandleEvent.class, PRICE_CLOSE), close)
                .set(Select.field(CandleEvent.class, PRICE_VOLUME), volume)
                .set(Select.field(CandleEvent.class, "closed"), closed)
                .create();
    }
}
