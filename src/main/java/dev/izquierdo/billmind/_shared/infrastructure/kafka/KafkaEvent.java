package dev.izquierdo.billmind._shared.infrastructure.kafka;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface KafkaEvent {
    String eventId();
}