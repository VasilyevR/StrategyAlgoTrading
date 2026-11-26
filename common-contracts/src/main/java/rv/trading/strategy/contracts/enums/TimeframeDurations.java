package rv.trading.strategy.contracts.enums;

import java.time.Duration;

public final class TimeframeDurations {

    private static final Duration DEFAULT_DURATION = Duration.ofMinutes(1);

    private TimeframeDurations() {
    }

    public static Duration duration(TimeframeCode timeframeCode) {
        if (timeframeCode == null) {
            return DEFAULT_DURATION;
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
}
