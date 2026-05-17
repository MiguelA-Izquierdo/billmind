package dev.izquierdo.billmind.invoice.application.query;

import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import dev.izquierdo.billmind.invoice.application.usecase.GetInvoiceUseCase;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class GetInvoiceQueryHandler implements QueryHandler<GetInvoiceQuery, Invoice> {

    private final GetInvoiceUseCase getInvoiceUseCase;

    public GetInvoiceQueryHandler(GetInvoiceUseCase getInvoiceUseCase) {
        this.getInvoiceUseCase = Objects.requireNonNull(getInvoiceUseCase, "GetInvoiceUseCase cannot be null");
    }

    @Override
    public Invoice handle(GetInvoiceQuery query) {
        return getInvoiceUseCase.execute(query.invoiceId(), query.sessionId());
    }

    @Override
    public Class<GetInvoiceQuery> queryType() {
        return GetInvoiceQuery.class;
    }
}