package rv.trading.strategy.engine.core.indicator;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.function.IntToDoubleFunction;

public final class IndicatorTestSeries {

    private IndicatorTestSeries() {
    }

    public static BarSeries of(int count, IntToDoubleFunction openAt, IntToDoubleFunction highAt,
                               IntToDoubleFunction lowAt, IntToDoubleFunction closeAt) {
        BarSeries series = new BaseBarSeriesBuilder().withName("indicator-test").build();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        for (int index = 0; index < count; index++) {
            Instant begin = start.plus(Duration.ofMinutes(index));
            series.barBuilder()
                    .timePeriod(Duration.ofMinutes(1))
                    .beginTime(begin)
                    .openPrice(openAt.applyAsDouble(index))
                    .highPrice(highAt.applyAsDouble(index))
                    .lowPrice(lowAt.applyAsDouble(index))
                    .closePrice(closeAt.applyAsDouble(index))
                    .volume(100.0)
                    .add();
        }
        return series;
    }

    public static BarSeries fromCloses(double... closes) {
        return of(closes.length,
                index -> index == 0 ? closes[0] : closes[index - 1],
                index -> Math.max(index == 0 ? closes[0] : closes[index - 1], closes[index]) + 1.0,
                index -> Math.min(index == 0 ? closes[0] : closes[index - 1], closes[index]) - 1.0,
                index -> closes[index]);
    }

    public static BarSeries rising(int count) {
        return fromCloses(java.util.stream.IntStream.range(0, count).mapToDouble(i -> 100.0 + i).toArray());
    }

    public static BarSeries falling(int count) {
        return fromCloses(java.util.stream.IntStream.range(0, count).mapToDouble(i -> 200.0 - i).toArray());
    }
}
