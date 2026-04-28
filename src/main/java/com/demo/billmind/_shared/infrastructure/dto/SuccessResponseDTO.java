package com.demo.billmind._shared.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SuccessResponseDTO(
        boolean success,
        int status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object data
) {
    public static SuccessResponseDTO of(int status, String message) {
        return new SuccessResponseDTO(true, status, message, null);
    }

    public static SuccessResponseDTO of(int status, String message, Object data) {
        return new SuccessResponseDTO(true, status, message, data);
    }
}
