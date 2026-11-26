package rv.trading.strategy.collector.config;

import org.springframework.boot.kafka.autoconfigure.KafkaConfigBuilder;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class MarketKafkaConfig {

    @Bean
    public ProducerFactory<Object, Object> producerFactory(KafkaProperties kafkaProperties,
                                                            JsonMapper jsonMapper) {
        return new DefaultKafkaProducerFactory<>(
                KafkaConfigBuilder.of(kafkaProperties).producer().build(),
                null,
                new JacksonJsonSerializer<>(jsonMapper)
        );
    }
}
