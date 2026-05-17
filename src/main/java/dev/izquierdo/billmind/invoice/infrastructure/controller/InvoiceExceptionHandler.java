package dev.izquierdo.billmind.invoice.infrastructure.controller;

import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceFieldExtractionException;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceNotFoundException;
import dev.izquierdo.billmind.invoice.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
class InvoiceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(InvoiceExceptionHandler.class);

    @ExceptionHandler(InvoiceNotFoundException.class)
    ResponseEntity<ErrorResponseDTO> handleInvoiceNotFound(InvoiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponseDTO.of(HttpStatus.NOT_FOUND.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(LlmServiceUnavailableException.class)
    ResponseEntity<ErrorResponseDTO> handleLlmServiceUnavailable(LlmServiceUnavailableException ex) {
        logException(ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ErrorResponseDTO.of(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(InvoiceFieldExtractionException.class)
    ResponseEntity<ErrorResponseDTO> handleInvoiceFieldExtraction(InvoiceFieldExtractionException ex) {
        logException(ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(NotASupplyInvoiceException.class)
    ResponseEntity<ErrorResponseDTO> handleNotASupplyInvoice(NotASupplyInvoiceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage())
        );
    }

    private void logException(Exception ex) {
        StackTraceElement origin = ex.getStackTrace().length > 0 ? ex.getStackTrace()[0] : null;
        if (origin != null) {
            log.error("Exception: {} | Cause: {} | File: {} | Line: {}",
                    ex.getMessage(),
                    ex.getCause() != null ? ex.getCause().toString() : "N/A",
                    origin.getFileName(),
                    origin.getLineNumber(),
                    ex);
        } else {
            log.error("Exception: {}", ex.getMessage(), ex);
        }
    }
}