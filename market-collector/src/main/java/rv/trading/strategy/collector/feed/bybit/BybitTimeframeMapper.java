package rv.trading.strategy.collector.feed.bybit;

import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.util.Optional;

public final class BybitTimeframeMapper {

    private BybitTimeframeMapper() {
    }

    public static String map(TimeframeCode timeframeCode) {
        return switch (timeframeCode) {
            case M1 -> BybitConstants.INTERVAL_1_MINUTE;
            case M5 -> BybitConstants.INTERVAL_5_MINUTES;
            case M15 -> BybitConstants.INTERVAL_15_MINUTES;
            case H1 -> BybitConstants.INTERVAL_1_HOUR;
            case H4 -> BybitConstants.INTERVAL_4_HOURS;
            case D1 -> BybitConstants.INTERVAL_1_DAY;
            case W1 -> BybitConstants.INTERVAL_1_WEEK;
        };
    }

    public static Optional<TimeframeCode> reverse(String interval) {
        if (interval == null) {
            return Optional.empty();
        }
        for (TimeframeCode timeframeCode : TimeframeCode.values()) {
            if (map(timeframeCode).equals(interval)) {
                return Optional.of(timeframeCode);
            }
        }
        return Optional.empty();
    }
}
