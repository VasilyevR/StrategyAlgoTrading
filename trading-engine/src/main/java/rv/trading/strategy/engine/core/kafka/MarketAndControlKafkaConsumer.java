package rv.trading.strategy.engine.core.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.event.BotControlEvent;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.KafkaTopics;
import rv.trading.strategy.contracts.event.TickEvent;
import rv.trading.strategy.engine.core.worker.AlgotradingWorkerManager;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MarketAndControlKafkaConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MarketAndControlKafkaConsumer.class);
    private final AlgotradingWorkerManager workerManager;
    private final AtomicBoolean firstTickLogged = new AtomicBoolean(false);

    public MarketAndControlKafkaConsumer(AlgotradingWorkerManager workerManager) {
        this.workerManager = workerManager;
    }

    @KafkaListener(topics = KafkaTopics.MARKET_TICKS,
            properties = "spring.json.value.default.type=rv.trading.strategy.contracts.event.TickEvent")
    public void onTick(TickEvent tick, Acknowledgment acknowledgment) {

        if (tick != null && firstTickLogged.compareAndSet(false, true)) {
            LOGGER.debug("First tick received from Kafka: pairId={}, symbol={}, price={}, ts={}",
                    tick.instrumentPairId(), tick.symbol(), tick.lastPrice(), tick.timestamp());
        }

        workerManager.dispatchTick(tick);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = KafkaTopics.MARKET_CANDLES,
            properties = "spring.json.value.default.type=rv.trading.strategy.contracts.event.CandleEvent")
    public void onCandle(CandleEvent candle, Acknowledgment acknowledgment) {

        if (candle != null) {
            LOGGER.debug("Candle received from Kafka: pairId={}, tf={}, openTime={}, close={}, closed={}",
                    candle.instrumentPairId(), candle.timeframeCode(), candle.openTime(),
                    candle.close(), candle.closed());
        }

        workerManager.dispatchCandle(candle);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = KafkaTopics.BOT_CONTROL,
            properties = "spring.json.value.default.type=rv.trading.strategy.contracts.event.BotControlEvent")
    public void onControl(BotControlEvent event, Acknowledgment acknowledgment) {

        LOGGER.debug("Control event received from Kafka: {}", event);

        workerManager.handleControlEvent(event);
        acknowledgment.acknowledge();
    }
}
