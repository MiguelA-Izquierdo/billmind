package com.demo.billmind._shared.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

public record ErrorResponseDTO(
        boolean success,
        int status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Map<String, String>> errors,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object data
) {
    public static ErrorResponseDTO of(int status, String message) {
        return new ErrorResponseDTO(false, status, message, null, null);
    }

    public static ErrorResponseDTO of(int status, String message, Map<String, Map<String, String>> errors) {
        return new ErrorResponseDTO(false, status, message, errors, null);
    }

    public static ErrorResponseDTO of(int status, String message, Map<String, Map<String, String>> errors, Object data) {
        return new ErrorResponseDTO(false, status, message, errors, data);
    }
}
