package com.demo.billmind.invoice.infrastructure.adapter.classifier;

import com.demo.billmind.invoice.domain.model.InvoiceClassification;
import com.demo.billmind.invoice.domain.model.InvoiceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmResponseParser {

    private static final Logger log = LoggerFactory.getLogger(LlmResponseParser.class);

    private final ObjectMapper objectMapper;

    public LlmResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public InvoiceClassification parse(String response) {
        try {
            String json    = extractJson(response);
            JsonNode node  = objectMapper.readTree(json);
            InvoiceType type = parseType(node.path("tipo").asText("OTRO"));
            String company   = node.path("compania").asText("").trim();
            return new InvoiceClassification(type, company);
        } catch (Exception e) {
            log.warn("No se pudo parsear la respuesta del LLM, clasificando como OTRO. Respuesta: {}", response);
            return new InvoiceClassification(InvoiceType.OTRO, "");
        }
    }

    private String extractJson(String raw) {
        String cleaned = raw.replace("```json", "").replace("```", "").strip();
        int start = cleaned.indexOf('{');
        int end   = cleaned.lastIndexOf('}') + 1;
        if (start == -1 || end == 0) {
            throw new IllegalArgumentException("No se encontró JSON en la respuesta del LLM");
        }
        return cleaned.substring(start, end);
    }

    private InvoiceType parseType(String rawType) {
        try {
            return InvoiceType.valueOf(rawType.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return InvoiceType.OTRO;
        }
    }
}
