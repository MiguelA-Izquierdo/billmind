package dev.izquierdo.billmind.invoice.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.izquierdo.billmind._shared.domain.model.fields.MobileLine;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class MobileLineListConverter implements AttributeConverter<List<MobileLine>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public String convertToDatabaseColumn(List<MobileLine> lines) {
        if (lines == null || lines.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(lines);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize mobile lines", e);
        }
    }

    @Override
    public List<MobileLine> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to deserialize mobile lines", e);
        }
    }
}