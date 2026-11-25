package rv.trading.strategy.engine.core.port;

import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.enums.TimeframeCode;

import java.util.List;

public interface MarketHistoryPort {
    List<CandleEvent> loadRecentCandles(long instrumentPairId, TimeframeCode timeframeCode, int limit);
}
