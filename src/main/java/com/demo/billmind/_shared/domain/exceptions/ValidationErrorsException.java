package com.demo.billmind._shared.domain.exceptions;

import java.util.Map;

public class ValidationErrorsException extends RuntimeException {

    private final Map<String, Map<String, String>> errors;

    public ValidationErrorsException(Map<String, Map<String, String>> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public Map<String, Map<String, String>> getErrors() {
        return errors;
    }
}
