package rv.trading.strategy.engine.core.sizing;

import lombok.Getter;

@Getter
public class OrderSizingException extends RuntimeException {
    private final boolean insufficientFunds;

    public OrderSizingException(String message) {
        this(message, false);
    }

    public OrderSizingException(String message, boolean insufficientFunds) {
        super(message);
        this.insufficientFunds = insufficientFunds;
    }
}
