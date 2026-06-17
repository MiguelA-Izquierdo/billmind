package dev.izquierdo.billmind.market.infrastructure.kafka.electricity;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.infrastructure.kafka.KafkaEvent;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.market.application.command.SaveElectricityRateCommand;
import dev.izquierdo.billmind.market.domain.exceptions.InvalidElectricityRateException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class ElectricityPriceConsumer {

    private static final Logger log = LoggerFactory.getLogger(ElectricityPriceConsumer.class);

    private final CommandBus commandBus;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String domainErrorsTopic;

    public ElectricityPriceConsumer(
            CommandBus commandBus,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topics.electricity-price-domain-errors}") String domainErrorsTopic) {
        this.commandBus        = commandBus;
        this.kafkaTemplate     = kafkaTemplate;
        this.domainErrorsTopic = domainErrorsTopic;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2),
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltTopicSuffix = ".DLT",
        // ElectricityPriceDltConsumer handles the DLT with StringDeserializer.
        // Without autoStartDltHandler=false, @RetryableTopic auto-creates a second
        // billmind-market.DLT consumer group using JsonDeserializer<KafkaEvent>, which
        // fails on the Base64-wrapped DLT payloads and produces spurious ERROR/WARN logs.
        autoStartDltHandler = "false",
        // Bad data that won't improve with retries — go straight to DLT.
        // IllegalArgumentException: invalid/null eventId or invalid UUID format.
        // IllegalStateException: unknown event type on this topic.
        exclude = {IllegalStateException.class, IllegalArgumentException.class}
    )
    @KafkaListener(topics = "${kafka.topics.electricity-price-updated}", groupId = "billmind-market")
    public void consume(ConsumerRecord<String, KafkaEvent> record) {
        if (!(record.value() instanceof ElectricityPriceEvent event)) {
            String valueType = record.value() == null ? "null (deserialization failed)" : record.value().getClass().getSimpleName();
            throw new IllegalStateException("Unexpected event type on " + record.topic() + ": " + valueType);
        }
        log.debug("Received electricity price event: eventId={} company={} tariff={}",
            event.eventId(), event.company(), event.tariffName());

        try {
            SaveElectricityRateCommand command = toCommand(event);
            commandBus.dispatch(command);
            log.debug("Electricity rate command dispatched: company={} tariff={} validFrom={}",
                event.company(), event.tariffName(), event.validFrom());
        } catch (InvalidElectricityRateException ex) {
            log.warn("Domain validation failed for electricity price event: eventId={} reason={}",
                event.eventId(), ex.getMessage());
            kafkaTemplate.send(domainErrorsTopic, new ElectricityPriceDomainErrorEvent(event, ex.getMessage()))
                .whenComplete((result, sendEx) -> {
                    if (sendEx != null) {
                        log.error("Failed to publish domain error for eventId={}: {}", event.eventId(), sendEx.getMessage());
                    }
                });
        }
    }

    private SaveElectricityRateCommand toCommand(ElectricityPriceEvent event) {
        if (event.eventId() == null)
            throw new IllegalArgumentException("eventId cannot be null");
        UUID id = UUID.fromString(event.eventId());
        if (event.company() == null)
            throw new InvalidElectricityRateException("company cannot be null");
        if (event.tariffName() == null)
            throw new InvalidElectricityRateException("tariffName cannot be null");
        if (event.pricePerKwh() == null && event.pricePerKwhValle() == null)
            throw new InvalidElectricityRateException("pricePerKwh or pricePerKwhValle must be provided");
        if (event.pricePerKwh() != null && event.pricePerKwh().signum() < 0)
            throw new InvalidElectricityRateException("pricePerKwh must be non-negative: " + event.pricePerKwh());
        if (event.validFrom() == null)
            throw new InvalidElectricityRateException("validFrom cannot be null");
        if (event.source() == null)
            throw new InvalidElectricityRateException("source cannot be null");
        return new SaveElectricityRateCommand(
            id,
            SupplyDomain.ELECTRICITY,
            event.company(),
            event.tariffName(),
            event.pricePerKwh(),
            event.pricePerKwhValle(),
            event.pricePerKwhLlano(),
            event.pricePerKwhPunta(),
            event.contractedPowerPrice(),
            event.contractedPowerPriceP2(),
            event.validFrom(),
            event.validTo(),
            event.region(),
            event.source(),
            event.publishedAt()
        );
    }
}