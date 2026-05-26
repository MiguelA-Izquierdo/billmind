package dev.izquierdo.billmind._shared.infrastructure;

import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;
import dev.izquierdo.billmind._shared.infrastructure.dto.ErrorResponseDTO;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceFieldExtractionException;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceNotFoundException;
import dev.izquierdo.billmind.invoice.domain.exceptions.LlmServiceUnavailableException;
import dev.izquierdo.billmind.invoice.domain.exceptions.NotASupplyInvoiceException;
import dev.izquierdo.billmind.invoice.domain.exceptions.UnsupportedSupplyTypeException;
import dev.izquierdo.billmind.market.domain.exceptions.InvalidElectricityRateException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleAllExceptions(Exception ex) {
        logException(ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponseDTO.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Se ha producido un error interno en el servidor")
        );
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvoiceNotFoundException(InvoiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponseDTO.of(HttpStatus.NOT_FOUND.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(NotASupplyInvoiceException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotASupplyInvoiceException(NotASupplyInvoiceException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(UnsupportedSupplyTypeException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnsupportedSupplyTypeException(UnsupportedSupplyTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(InvoiceFieldExtractionException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvoiceFieldExtractionException(InvoiceFieldExtractionException ex) {
        logException(ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(LlmServiceUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleLlmServiceUnavailableException(LlmServiceUnavailableException ex) {
        logException(ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ErrorResponseDTO.of(HttpStatus.SERVICE_UNAVAILABLE.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(InvalidElectricityRateException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidElectricityRateException(InvalidElectricityRateException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ErrorResponseDTO.of(HttpStatus.UNPROCESSABLE_ENTITY.value(), ex.getMessage())
        );
    }

    @ExceptionHandler(ValidationErrorsException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrorsException(ValidationErrorsException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), ex.getErrors())
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), "El campo '" + ex.getRequestPartName() + "' es obligatorio")
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), "El archivo supera el tamaño máximo permitido (5 MB)")
        );
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponseDTO> handleMultipartException(MultipartException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), "La petición debe ser multipart/form-data e incluir el campo 'file'")
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        Map<String, Map<String, String>> errors = new HashMap<>();

        Throwable cause = ex.getCause();
        if (cause instanceof InvalidFormatException invalidFormatEx && !invalidFormatEx.getPath().isEmpty()) {
            String fieldName = invalidFormatEx.getPath().get(0).getFieldName();
            errors.put(fieldName, Map.of("invalid", "Se esperaba un número, pero se recibió: " + invalidFormatEx.getValue()));
        } else {
            errors.put("body", Map.of("invalid", "El formato de la petición no es válido."));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDTO.of(HttpStatus.BAD_REQUEST.value(), "Error de validación en la petición", errors)
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

