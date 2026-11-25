package rv.trading.strategy.engine.core.indicator.exception;

import rv.trading.strategy.contracts.enums.IndicatorCode;

public final class IndicatorEvaluationException extends RuntimeException {

    private final IndicatorCode indicatorCode;

    public IndicatorEvaluationException(IndicatorCode indicatorCode, Throwable cause) {
        super("Evaluation failed for indicator " + indicatorCode, cause);
        this.indicatorCode = indicatorCode;
    }

    public IndicatorCode getIndicatorCode() {
        return indicatorCode;
    }
}
