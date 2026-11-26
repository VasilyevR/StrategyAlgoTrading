package rv.trading.strategy.collector.publisher;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import rv.trading.strategy.contracts.event.CandleEvent;
import rv.trading.strategy.contracts.event.KafkaTopics;
import rv.trading.strategy.contracts.event.TickEvent;

@Component
public class MarketEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public MarketEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TickEvent event) {
        kafkaTemplate.send(KafkaTopics.MARKET_TICKS,
                String.valueOf(event.instrumentPairId()), event);
    }

    public void publish(CandleEvent event) {
        kafkaTemplate.send(KafkaTopics.MARKET_CANDLES,
                String.valueOf(event.instrumentPairId()), event);
    }
}
