package dev.izquierdo.billmind.invoice.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.infrastructure.dto.SuccessResponseDTO;
import dev.izquierdo.billmind.invoice.application.command.UploadInvoiceCommand;
import dev.izquierdo.billmind.invoice.infrastructure.controller.dto.InvoiceUploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final CommandBus commandBus;

    public InvoiceController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/upload")
    public ResponseEntity<SuccessResponseDTO> uploadInvoice(
            @RequestParam("file") MultipartFile file) throws IOException {

        UUID invoiceId = UUID.randomUUID();
        UploadInvoiceCommand command = new UploadInvoiceCommand(invoiceId, file.getOriginalFilename(), file.getBytes());
        commandBus.dispatch(command);

        return ResponseEntity.status(201).body(SuccessResponseDTO.of(
                201,
                "Invoice uploaded and vectorized successfully.",
                new InvoiceUploadResponse(invoiceId, command.fileName())
        ));
    }
}
