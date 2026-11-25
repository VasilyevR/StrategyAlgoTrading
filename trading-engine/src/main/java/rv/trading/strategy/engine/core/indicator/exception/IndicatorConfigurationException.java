package rv.trading.strategy.engine.core.indicator.exception;

import rv.trading.strategy.contracts.enums.IndicatorCode;

public final class IndicatorConfigurationException extends RuntimeException {

    private final IndicatorCode indicatorCode;

    public IndicatorConfigurationException(IndicatorCode indicatorCode, String message) {
        super(message);
        this.indicatorCode = indicatorCode;
    }

    public IndicatorConfigurationException(IndicatorCode indicatorCode, String message, Throwable cause) {
        super(message, cause);
        this.indicatorCode = indicatorCode;
    }

    public IndicatorCode getIndicatorCode() {
        return indicatorCode;
    }
}
