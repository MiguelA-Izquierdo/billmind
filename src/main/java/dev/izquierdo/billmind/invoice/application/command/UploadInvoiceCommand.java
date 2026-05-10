package dev.izquierdo.billmind.invoice.application.command;

import dev.izquierdo.billmind._shared.application.command.Command;
import dev.izquierdo.billmind._shared.domain.exceptions.ValidationErrorsException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record UploadInvoiceCommand(UUID invoiceId, String fileName, byte[] fileContent) implements Command {

    public UploadInvoiceCommand {
        Map<String, Map<String, String>> errors = new HashMap<>();

        if (invoiceId == null) {
            errors.put("invoiceId", Map.of("null", "El identificador de la factura no puede ser nulo"));
        }
        if (fileName == null || fileName.isBlank()) {
            errors.put("fileName", Map.of("blank", "El nombre del archivo no puede estar vacío"));
        }
        if (fileContent == null || fileContent.length == 0) {
            errors.put("file", Map.of("empty", "El archivo no puede estar vacío"));
        } else if (!isPdf(fileContent)) {
            errors.put("file", Map.of("invalidFormat", "El archivo debe ser un PDF válido"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationErrorsException(errors);
        }
    }

    private static boolean isPdf(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 0x25   // %
                && bytes[1] == 0x50   // P
                && bytes[2] == 0x44   // D
                && bytes[3] == 0x46;  // F
    }
}
