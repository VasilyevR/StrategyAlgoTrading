package rv.trading.strategy.collector.feed.bybit;

import org.junit.jupiter.api.Test;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BybitTimeframeMapperTest {

    @Test
    void mapsEveryTimeframeToTheBybitIntervalAndBack() {
        Map<TimeframeCode, String> intervals = Map.of(
                TimeframeCode.M1, BybitConstants.INTERVAL_1_MINUTE,
                TimeframeCode.M5, BybitConstants.INTERVAL_5_MINUTES,
                TimeframeCode.M15, BybitConstants.INTERVAL_15_MINUTES,
                TimeframeCode.H1, BybitConstants.INTERVAL_1_HOUR,
                TimeframeCode.H4, BybitConstants.INTERVAL_4_HOURS,
                TimeframeCode.D1, BybitConstants.INTERVAL_1_DAY,
                TimeframeCode.W1, BybitConstants.INTERVAL_1_WEEK
        );

        for (TimeframeCode timeframeCode : TimeframeCode.values()) {
            String interval = intervals.get(timeframeCode);
            assertThat(BybitTimeframeMapper.map(timeframeCode)).isEqualTo(interval);
            assertThat(BybitTimeframeMapper.reverse(interval)).contains(timeframeCode);
        }
    }

    @Test
    void returnsEmptyForUnknownOrNullInterval() {
        assertThat(BybitTimeframeMapper.reverse("unknown-interval")).isEmpty();
        assertThat(BybitTimeframeMapper.reverse(null)).isEmpty();
    }
}
