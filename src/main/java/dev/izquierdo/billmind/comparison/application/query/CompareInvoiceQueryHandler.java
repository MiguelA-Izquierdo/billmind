package dev.izquierdo.billmind.comparison.application.query;

import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import dev.izquierdo.billmind.comparison.application.usecase.CompareInvoiceUseCase;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class CompareInvoiceQueryHandler
        implements QueryHandler<CompareInvoiceQuery, Optional<ComparisonResult>> {

    private final CompareInvoiceUseCase compareInvoiceUseCase;

    public CompareInvoiceQueryHandler(CompareInvoiceUseCase compareInvoiceUseCase) {
        this.compareInvoiceUseCase = Objects.requireNonNull(compareInvoiceUseCase,
                "CompareInvoiceUseCase cannot be null");
    }

    @Override
    public Optional<ComparisonResult> handle(CompareInvoiceQuery query) {
        return compareInvoiceUseCase.compare(query.fields());
    }

    @Override
    public Class<CompareInvoiceQuery> queryType() {
        return CompareInvoiceQuery.class;
    }
}