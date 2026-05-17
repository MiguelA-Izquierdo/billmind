package dev.izquierdo.billmind.invoice.application.query;

import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import dev.izquierdo.billmind.invoice.application.usecase.GetSessionInvoicesUseCase;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class GetSessionInvoicesQueryHandler implements QueryHandler<GetSessionInvoicesQuery, List<Invoice>> {

    private final GetSessionInvoicesUseCase getSessionInvoicesUseCase;

    public GetSessionInvoicesQueryHandler(GetSessionInvoicesUseCase getSessionInvoicesUseCase) {
        this.getSessionInvoicesUseCase = Objects.requireNonNull(getSessionInvoicesUseCase, "GetSessionInvoicesUseCase cannot be null");
    }

    @Override
    public List<Invoice> handle(GetSessionInvoicesQuery query) {
        return getSessionInvoicesUseCase.execute(query.sessionId());
    }

    @Override
    public Class<GetSessionInvoicesQuery> queryType() {
        return GetSessionInvoicesQuery.class;
    }
}