package rv.trading.strategy.collector.feed.bybit;

public final class BybitConstants {

    public static final String REST_KLINE_PATH = "/v5/market/kline";
    public static final String SPOT_CATEGORY = "spot";
    public static final String LINEAR_CATEGORY = "linear";

    public static final String OPERATION_KEY = "op";
    public static final String ARGS_KEY = "args";
    public static final String TOPIC_KEY = "topic";
    public static final String DATA_KEY = "data";
    public static final String RET_CODE_KEY = "retCode";
    public static final String RESULT_KEY = "result";
    public static final String LIST_KEY = "list";
    public static final String SYMBOL_KEY = "symbol";
    public static final String TIMESTAMP_KEY = "ts";
    public static final String LAST_PRICE_KEY = "lastPrice";
    public static final String BID_ONE_PRICE_KEY = "bid1Price";
    public static final String ASK_ONE_PRICE_KEY = "ask1Price";
    public static final String START_KEY = "start";
    public static final String END_KEY = "end";
    public static final String OPEN_KEY = "open";
    public static final String HIGH_KEY = "high";
    public static final String LOW_KEY = "low";
    public static final String CLOSE_KEY = "close";
    public static final String VOLUME_KEY = "volume";
    public static final String CONFIRM_KEY = "confirm";
    public static final String INTERVAL_KEY = "interval";
    public static final String CATEGORY_KEY = "category";
    public static final String LIMIT_KEY = "limit";
    public static final String TOPIC_DELIMITER = ".";
    public static final String TOPIC_DELIMITER_REGEX = "\\.";
    public static final String INTERVAL_1_MINUTE = "1";
    public static final String INTERVAL_5_MINUTES = "5";
    public static final String INTERVAL_15_MINUTES = "15";
    public static final String INTERVAL_1_HOUR = "60";
    public static final String INTERVAL_4_HOURS = "240";
    public static final String INTERVAL_1_DAY = "D";
    public static final String INTERVAL_1_WEEK = "W";
    public static final String TICKER_TOPIC_PREFIX = "tickers.";
    public static final String KLINE_TOPIC_PREFIX = "kline.";
    public static final String WEBSOCKET_SHUTDOWN_REASON = "shutdown";

    public static final String OP_SUBSCRIBE = "subscribe";
    public static final String OP_PING = "ping";

    private BybitConstants() {
    }
}
