package dev.izquierdo.billmind.market.application.query;

import dev.izquierdo.billmind._shared.application.query.QueryHandler;
import dev.izquierdo.billmind.market.application.usecase.GetElectricityRatesUseCase;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class GetElectricityRatesQueryHandler implements QueryHandler<GetElectricityRatesQuery, List<ElectricityRate>> {

    private final GetElectricityRatesUseCase useCase;

    public GetElectricityRatesQueryHandler(GetElectricityRatesUseCase useCase) {
        this.useCase = Objects.requireNonNull(useCase, "GetElectricityRatesUseCase cannot be null");
    }

    @Override
    public List<ElectricityRate> handle(GetElectricityRatesQuery query) {
        return useCase.execute();
    }

    @Override
    public Class<GetElectricityRatesQuery> queryType() {
        return GetElectricityRatesQuery.class;
    }
}