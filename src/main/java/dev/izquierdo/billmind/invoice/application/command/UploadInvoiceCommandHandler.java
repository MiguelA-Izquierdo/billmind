package dev.izquierdo.billmind.invoice.application.command;

import dev.izquierdo.billmind._shared.application.command.CommandHandler;
import dev.izquierdo.billmind.invoice.application.usecase.UploadInvoiceUseCase;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UploadInvoiceCommandHandler implements CommandHandler<UploadInvoiceCommand> {

    private final UploadInvoiceUseCase uploadInvoiceUseCase;

    public UploadInvoiceCommandHandler(UploadInvoiceUseCase uploadInvoiceUseCase) {
        this.uploadInvoiceUseCase = Objects.requireNonNull(uploadInvoiceUseCase, "UploadInvoiceUseCase cannot be null");
    }

    @Override
    public Class<UploadInvoiceCommand> commandType() {
        return UploadInvoiceCommand.class;
    }

    @Override
    public void handle(UploadInvoiceCommand command) {
        Invoice invoice = Invoice.builder(command.invoiceId(), command.fileName())
                .sessionId(command.sessionId())
                .build();
        uploadInvoiceUseCase.upload(invoice, command.fileContent());
    }
}
