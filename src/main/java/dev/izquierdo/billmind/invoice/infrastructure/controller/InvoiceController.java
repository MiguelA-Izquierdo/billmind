package dev.izquierdo.billmind.invoice.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.infrastructure.dto.SuccessResponseDTO;
import dev.izquierdo.billmind._shared.infrastructure.session.SessionContext;
import dev.izquierdo.billmind.comparison.application.query.CompareInvoiceQuery;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.infrastructure.controller.dto.ComparisonResponseDTO;
import dev.izquierdo.billmind.invoice.application.command.UploadInvoiceCommand;
import dev.izquierdo.billmind.invoice.application.query.GetInvoiceQuery;
import dev.izquierdo.billmind.invoice.application.query.GetSessionInvoicesQuery;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.infrastructure.controller.dto.InvoiceResponse;
import dev.izquierdo.billmind.invoice.infrastructure.controller.dto.InvoiceUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final CommandBus commandBus;
    private final QueryBus queryBus;
    private final SessionContext sessionContext;

    public InvoiceController(CommandBus commandBus, QueryBus queryBus,
                              SessionContext sessionContext) {
        this.commandBus     = commandBus;
        this.queryBus       = queryBus;
        this.sessionContext = sessionContext;
    }

    @PostMapping
    public ResponseEntity<SuccessResponseDTO> uploadInvoice(
            @RequestParam("file") MultipartFile file) throws IOException {

        UUID invoiceId = UUID.randomUUID();
        UUID sessionId = sessionContext.getSessionId();
        UploadInvoiceCommand command = new UploadInvoiceCommand(invoiceId, sessionId, file.getOriginalFilename(), file.getBytes());
        commandBus.dispatch(command);

        Invoice invoice = queryBus.dispatch(new GetInvoiceQuery(invoiceId, sessionId));
        ComparisonResponseDTO comparison = null;
        if (invoice.getFields() == null) {
            log.info("Comparison skipped for invoice={} — field extraction produced no data", invoiceId);
        } else {
            Optional<ComparisonResult> comparisonResult = queryBus.dispatch(new CompareInvoiceQuery(invoice.getFields()));
            comparison = comparisonResult
                    .map(result -> {
                        log.debug("Comparison completed for invoice={} savings={}€ bestRate={}/{}",
                                invoiceId, result.annualSavingsEuros(),
                                result.bestCompany(), result.bestTariffName());
                        return ComparisonResponseDTO.from(result);
                    })
                    .orElseGet(() -> {
                        log.debug("Comparison skipped for invoice={} supplyType={} — insufficient data for comparison (pricePerKwh or consumptionKwh missing, or no market rates)",
                                invoiceId, invoice.getSupplyType());
                        return null;
                    });
        }

        return ResponseEntity.status(201).body(SuccessResponseDTO.of(
                201,
                "Factura subida y procesada correctamente.",
                new InvoiceUploadResponse(invoiceId, command.fileName(), comparison)
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

    @GetMapping("/{id}/comparison")
    public ResponseEntity<SuccessResponseDTO> getComparison(@PathVariable UUID id) {
        Invoice invoice = queryBus.dispatch(new GetInvoiceQuery(id, sessionContext.getSessionId()));
        if (invoice.getFields() == null) {
            return ResponseEntity.ok(SuccessResponseDTO.of(200, "Sin datos de extracción para comparar.", null));
        }
        Optional<ComparisonResult> comparisonResult = queryBus.dispatch(new CompareInvoiceQuery(invoice.getFields()));
        ComparisonResponseDTO comparison = comparisonResult
                .map(r -> {
                    log.debug("Comparison on select invoice={} savings={}€", id, r.annualSavingsEuros());
                    return ComparisonResponseDTO.from(r);
                })
                .orElse(null);
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Comparación calculada.", comparison));
    }
}