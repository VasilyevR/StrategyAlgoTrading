package rv.trading.strategy.engine.core.worker;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class BarSeriesBuffer {
    public static final int DEFAULT_MAXIMUM_BAR_COUNT = 500;

    private final BaseBarSeries series;

    public BarSeriesBuffer() {
        this.series = (BaseBarSeries) new BaseBarSeriesBuilder()
                .withName("algotrading")
                .withMaxBarCount(DEFAULT_MAXIMUM_BAR_COUNT)
                .build();
    }

    public void initialize(List<CandleEvent> candles) {
        series.clear();
        if (candles == null) {
            return;
        }
        for (CandleEvent candle : candles) {
            if (candle != null) {
                applyCandle(candle);
            }
        }
    }

    public void applyTick(TickEvent tick, TimeframeCode timeframeCode) {
        if (tick == null || timeframeCode == null || tick.lastPrice() == null) {
            return;
        }
        Duration period = durationFor(timeframeCode);
        Instant begin = align(tick.timestamp(), period);
        Instant end = begin.plus(period);
        double price = tick.lastPrice().doubleValue();

        if (!series.isEmpty()) {
            Bar last = series.getLastBar();
            if (last.getBeginTime().equals(begin)) {
                double open = last.getOpenPrice().doubleValue();
                double high = Math.max(last.getHighPrice().doubleValue(), price);
                double low = Math.min(last.getLowPrice().doubleValue(), price);
                Bar replacement = series.barBuilder()
                        .timePeriod(period)
                        .beginTime(begin)
                        .endTime(end)
                        .openPrice(open)
                        .highPrice(high)
                        .lowPrice(low)
                        .closePrice(price)
                        .volume(last.getVolume().doubleValue())
                        .build();
                series.replaceBar(series.getEndIndex(), replacement);
                return;
            }
        }

        series.barBuilder()
                .timePeriod(period)
                .beginTime(begin)
                .endTime(end)
                .openPrice(price)
                .highPrice(price)
                .lowPrice(price)
                .closePrice(price)
                .volume(0)
                .add();
    }

    public void applyCandle(CandleEvent candle) {
        if (candle == null || candle.open() == null || candle.high() == null
                || candle.low() == null || candle.close() == null) {
            return;
        }
        Duration period = durationFor(candle.timeframeCode());
        Instant begin = candle.openTime() != null ? candle.openTime() : candle.closeTime();
        if (begin == null) {
            return;
        }
        Instant end = candle.closeTime() != null ? candle.closeTime() : begin.plus(period);
        Bar bar = series.barBuilder()
                .timePeriod(period)
                .beginTime(begin)
                .endTime(end)
                .openPrice(candle.open().doubleValue())
                .highPrice(candle.high().doubleValue())
                .lowPrice(candle.low().doubleValue())
                .closePrice(candle.close().doubleValue())
                .volume(candle.volume() == null ? 0 : candle.volume().doubleValue())
                .build();
        if (!series.isEmpty()) {
            Bar last = series.getLastBar();
            if (last.getBeginTime().equals(begin)) {
                series.replaceBar(series.getEndIndex(), bar);
                return;
            }
        }
        series.addBar(bar);
    }

    public BarSeries series() {
        return series;
    }

    private static Duration durationFor(TimeframeCode timeframeCode) {
        if (timeframeCode == null) {
            return Duration.ofMinutes(1);
        }
        return switch (timeframeCode) {
            case M1 -> Duration.ofMinutes(1);
            case M5 -> Duration.ofMinutes(5);
            case M15 -> Duration.ofMinutes(15);
            case H1 -> Duration.ofHours(1);
            case H4 -> Duration.ofHours(4);
            case D1 -> Duration.ofDays(1);
            case W1 -> Duration.ofDays(7);
        };
    }

    private static Instant align(Instant timestamp, Duration period) {
        if (timestamp == null) {
            return Instant.now().truncatedTo(ChronoUnit.SECONDS);
        }
        long seconds = period.toSeconds();
        long epochSecond = timestamp.getEpochSecond();
        long aligned = epochSecond - Math.floorMod(epochSecond, seconds);
        return Instant.ofEpochSecond(aligned);
    }
}
