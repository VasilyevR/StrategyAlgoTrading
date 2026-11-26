package rv.trading.strategy.contracts.enums;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimeframeDurationsTest {

    @Test
    void mapsEveryTimeframeToItsDuration() {
        Map<TimeframeCode, Duration> durations = Map.of(
                TimeframeCode.M1, Duration.ofMinutes(1),
                TimeframeCode.M5, Duration.ofMinutes(5),
                TimeframeCode.M15, Duration.ofMinutes(15),
                TimeframeCode.H1, Duration.ofHours(1),
                TimeframeCode.H4, Duration.ofHours(4),
                TimeframeCode.D1, Duration.ofDays(1),
                TimeframeCode.W1, Duration.ofDays(7)
        );

        for (TimeframeCode timeframeCode : TimeframeCode.values()) {
            assertThat(TimeframeDurations.duration(timeframeCode)).isEqualTo(durations.get(timeframeCode));
        }
    }

    @Test
    void fallsBackToOneMinuteForNullTimeframe() {
        assertThat(TimeframeDurations.duration(null)).isEqualTo(Duration.ofMinutes(1));
    }
}
