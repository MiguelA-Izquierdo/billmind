package com.demo.billmind.invoice.application.command;

import com.demo.billmind._shared.application.command.CommandHandler;
import com.demo.billmind.invoice.application.usecase.UploadInvoiceUseCase;
import com.demo.billmind.invoice.domain.model.Invoice;
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
        Invoice invoice = new Invoice(command.invoiceId(), command.fileName());
        uploadInvoiceUseCase.upload(invoice, command.fileContent());
    }
}
