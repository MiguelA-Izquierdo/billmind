package dev.izquierdo.billmind.invoice.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.infrastructure.dto.SuccessResponseDTO;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind.invoice.application.command.UploadInvoiceCommand;
import dev.izquierdo.billmind.invoice.application.query.GetInvoiceQuery;
import dev.izquierdo.billmind.invoice.application.query.GetSessionInvoicesQuery;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.infrastructure.controller.dto.InvoiceResponse;
import dev.izquierdo.billmind.invoice.infrastructure.controller.dto.InvoiceUploadResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final SessionContext sessionContext;

    public InvoiceController(CommandBus commandBus, QueryBus queryBus, SessionContext sessionContext) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
        this.sessionContext = sessionContext;
    }

    @PostMapping("/upload")
    public ResponseEntity<SuccessResponseDTO> uploadInvoice(
            @RequestParam("file") MultipartFile file) throws IOException {

        UUID invoiceId = UUID.randomUUID();
        UUID sessionId = sessionContext.getSessionId();
        UploadInvoiceCommand command = new UploadInvoiceCommand(invoiceId, sessionId, file.getOriginalFilename(), file.getBytes());
        commandBus.dispatch(command);

        return ResponseEntity.status(201).body(SuccessResponseDTO.of(
                201,
                "Factura subida y procesada correctamente.",
                new InvoiceUploadResponse(invoiceId, command.fileName())
        ));
    }

    @GetMapping
    public ResponseEntity<SuccessResponseDTO> getSessionInvoices() {
        List<Invoice> invoices = queryBus.dispatch(new GetSessionInvoicesQuery(sessionContext.getSessionId()));
        List<InvoiceResponse> data = invoices.stream().map(InvoiceResponse::from).toList();
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Facturas obtenidas correctamente", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuccessResponseDTO> getInvoice(@PathVariable UUID id) {
        Invoice invoice = queryBus.dispatch(new GetInvoiceQuery(id, sessionContext.getSessionId()));
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Factura obtenida correctamente", InvoiceResponse.from(invoice)));
    }
}