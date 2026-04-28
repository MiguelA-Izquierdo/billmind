package com.demo.billmind._shared.infrastructure;

import com.demo.billmind._shared.domain.exceptions.ValidationErrorsException;
import com.demo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import com.demo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public GlobalExceptionHandler() {
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<ErrorResponseDTO> handleNullPointerException(NullPointerException ex) {
        logException(ex);
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "A null value was encountered"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleAllExceptions(Exception ex) {
        logException(ex);
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal server error"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }


    @ExceptionHandler(NotASupplyInvoiceException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotASupplyInvoiceException(NotASupplyInvoiceException ex) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    @ExceptionHandler(ValidationErrorsException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrorsException(ValidationErrorsException ex) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                ex.getErrors()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingPart(MissingServletRequestPartException ex) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST.value(),
                "El campo '" + ex.getRequestPartName() + "' es obligatorio"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMultipartException(MultipartException ex) {
        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST.value(),
                "La petición debe ser multipart/form-data e incluir el campo 'file'"
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleInvalidFormatException(HttpMessageNotReadableException ex) {
        Map<String, Map<String, String>> errors = new HashMap<>();

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormatEx && !invalidFormatEx.getPath().isEmpty()) {
            String fieldName = invalidFormatEx.getPath().get(0).getFieldName();
            Map<String, String> fieldErrors = new HashMap<>();
            fieldErrors.put("invalid", "Expected a number, but got: " + invalidFormatEx.getValue());
            errors.put(fieldName, fieldErrors);
        } else {
            Map<String, String> generalErrors = new HashMap<>();
            generalErrors.put("invalid", "Invalid request format.");
            errors.put("body", generalErrors);
        }

        ErrorResponseDTO errorResponse = ErrorResponseDTO.of(
                HttpStatus.BAD_REQUEST.value(),
                "Validation failed",
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    private void logException(Exception ex) {
        StackTraceElement origin = ex.getStackTrace().length > 0
                ? ex.getStackTrace()[0]
                : null;

        if (origin != null) {
            log.error(
                    "Exception: {} | Cause: {} | File: {} | Line: {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().toString() : "N/A",
                    origin.getFileName(),
                    origin.getLineNumber(),
                    ex
            );
        } else {
            log.error("Exception: {}", ex.getMessage(), ex);
        }
    }

}

