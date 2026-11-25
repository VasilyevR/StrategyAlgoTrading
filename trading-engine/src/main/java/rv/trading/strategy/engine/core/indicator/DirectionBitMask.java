package rv.trading.strategy.engine.core.indicator;

import java.util.BitSet;

public final class DirectionBitMask {

    public static final int BIT_UP = 0;
    public static final int BIT_DOWN = 1;

    private DirectionBitMask() {
    }

    public static BitSet empty() {
        return new BitSet(2);
    }

    public static BitSet of(boolean up, boolean down) {
        BitSet mask = empty();
        if (up) {
            setUp(mask);
        }
        if (down) {
            setDown(mask);
        }
        return mask;
    }

    public static boolean hasUp(BitSet mask) {
        return mask.get(BIT_UP);
    }

    public static boolean hasDown(BitSet mask) {
        return mask.get(BIT_DOWN);
    }

    public static void setUp(BitSet mask) {
        mask.set(BIT_UP);
    }

    public static void setDown(BitSet mask) {
        mask.set(BIT_DOWN);
    }
}
