package dev.izquierdo.billmind.invoice.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.izquierdo.billmind.invoice.domain.model.fields.StreamingService;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class StreamingServiceListConverter implements AttributeConverter<List<StreamingService>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public String convertToDatabaseColumn(List<StreamingService> services) {
        if (services == null || services.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(services);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize streaming services", e);
        }
    }

    @Override
    public List<StreamingService> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize streaming services", e);
        }
    }
}