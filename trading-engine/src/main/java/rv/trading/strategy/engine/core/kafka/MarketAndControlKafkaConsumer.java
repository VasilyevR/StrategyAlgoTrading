package rv.trading.strategy.engine.core.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.event.BotControlEvent;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.KafkaTopics;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.engine.core.worker.AlgotradingWorkerManager;

@Component
public class MarketAndControlKafkaConsumer {
    private final AlgotradingWorkerManager workerManager;

    public MarketAndControlKafkaConsumer(AlgotradingWorkerManager workerManager) {
        this.workerManager = workerManager;
    }

    @KafkaListener(topics = KafkaTopics.MARKET_TICKS,
            properties = "spring.json.value.default.type=rv.trading.strategy.contracts.event.TickEvent")
    public void onTick(TickEvent tick, Acknowledgment acknowledgment) {
        workerManager.dispatchTick(tick);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = KafkaTopics.MARKET_CANDLES,
            properties = "spring.json.value.default.type=rv.trading.strategy.contracts.event.CandleEvent")
    public void onCandle(CandleEvent candle, Acknowledgment acknowledgment) {
        workerManager.dispatchCandle(candle);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = KafkaTopics.BOT_CONTROL,
            properties = "spring.json.value.default.type=rv.trading.strategy.contracts.event.BotControlEvent")
    public void onControl(BotControlEvent event, Acknowledgment acknowledgment) {
        workerManager.handleControlEvent(event);
        acknowledgment.acknowledge();
    }
}
