package dev.izquierdo.billmind.assistant.infrastructure.adapter;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.assistant.domain.model.ComparisonSummary;
import dev.izquierdo.billmind.assistant.domain.port.ComparisonContextPort;
import dev.izquierdo.billmind.comparison.application.usecase.CompareInvoiceUseCase;
import dev.izquierdo.billmind.comparison.domain.model.ComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityComparisonResult;
import dev.izquierdo.billmind.comparison.domain.model.ElectricityOfferBlock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Bridges the assistant to the deterministic {@link CompareInvoiceUseCase}, translating
 * the comparison bounded context's result into the assistant's own {@link ComparisonSummary}.
 */
@Component
public class ComparisonContextAdapter implements ComparisonContextPort {

    private final CompareInvoiceUseCase compareInvoiceUseCase;

    public ComparisonContextAdapter(CompareInvoiceUseCase compareInvoiceUseCase) {
        this.compareInvoiceUseCase = compareInvoiceUseCase;
    }

    @Override
    public Optional<ComparisonSummary> summarize(InvoiceFields fields) {
        return compareInvoiceUseCase.compare(fields).map(this::toSummary);
    }

    private ComparisonSummary toSummary(ComparisonResult result) {
        return switch (result) {
            case ElectricityComparisonResult e -> new ComparisonSummary(
                    e.userPricePerKwh(),
                    e.userIsTou(),
                    e.annualKwhEstimate(),
                    toBlock(e.flatBlock()),
                    toBlock(e.touBlock()));
        };
    }

    private ComparisonSummary.OfferBlock toBlock(ElectricityOfferBlock block) {
        if (block == null) return null;
        List<ComparisonSummary.Alternative> alternatives = block.alternatives().stream()
                .map(a -> new ComparisonSummary.Alternative(
                        a.company(), a.tariffName(), a.effectivePricePerKwh()))
                .toList();
        return new ComparisonSummary.OfferBlock(
                block.bestCompany(),
                block.bestTariffName(),
                block.bestPricePerKwh(),
                block.annualSavingsEuros(),
                alternatives);
    }
}