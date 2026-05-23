package dev.izquierdo.billmind.market.infrastructure.kafka;

import dev.izquierdo.billmind.market.infrastructure.kafka.electricity.ElectricityPriceEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
class MarketKafkaConfig {

    @Value("${kafka.topics.electricity-price-updated}")
    private String electricityPriceUpdatedTopic;

    @Value("${kafka.topics.electricity-price-domain-errors}")
    private String electricityPriceDomainErrorsTopic;

    @Value("${kafka.topics.partitions:1}")
    private int partitions;

    @Value("${kafka.topics.replicas:1}")
    private int replicas;

    @Bean
    Jackson2ObjectMapperBuilderCustomizer marketKafkaSubtypes() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.registerSubtypes(ElectricityPriceEvent.class));
    }

    @Bean
    NewTopic electricityPriceUpdatedTopic() {
        return TopicBuilder.name(electricityPriceUpdatedTopic)
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }

    // Infrastructure failures: deserialization errors, consumer crashes after retries
    @Bean
    NewTopic electricityPriceUpdatedDlt() {
        return TopicBuilder.name(electricityPriceUpdatedTopic + ".DLT")
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }

    // Domain validation failures: negative prices, invalid dates, etc.
    @Bean
    NewTopic electricityPriceDomainErrorsTopic() {
        return TopicBuilder.name(electricityPriceDomainErrorsTopic)
            .partitions(partitions)
            .replicas(replicas)
            .build();
    }
}