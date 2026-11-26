package rv.trading.strategy.contracts.event;

public final class KafkaTopics {

    public static final String MARKET_TICKS = "market.ticks";
    public static final String MARKET_CANDLES = "market.candles";
    public static final String BOT_CONTROL = "bot.control";

    private KafkaTopics() {
    }
}
