package rv.trading.strategy.engine.core.indicator;

public final class CrossUtils {

    private CrossUtils() {
    }

    public static boolean crossedUp(double prevX, double currX, double prevY, double currY) {
        return finite(prevX, currX, prevY, currY) && prevX <= prevY && currX > currY;
    }

    public static boolean crossedDown(double prevX, double currX, double prevY, double currY) {
        return finite(prevX, currX, prevY, currY) && prevX >= prevY && currX < currY;
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
