package dev.izquierdo.billmind.market.infrastructure.controller;

import dev.izquierdo.billmind._shared.application.command.CommandBus;
import dev.izquierdo.billmind._shared.application.query.QueryBus;
import dev.izquierdo.billmind._shared.infrastructure.dto.SuccessResponseDTO;
import dev.izquierdo.billmind.market.application.command.DeleteAllElectricityRatesCommand;
import dev.izquierdo.billmind.market.application.query.GetElectricityRatesQuery;
import dev.izquierdo.billmind.market.domain.model.ElectricityRate;
import dev.izquierdo.billmind.market.infrastructure.controller.dto.ElectricityRateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/market-rates")
public class ElectricityRateController {

    private final QueryBus queryBus;
    private final CommandBus commandBus;

    public ElectricityRateController(QueryBus queryBus, CommandBus commandBus) {
        this.queryBus = queryBus;
        this.commandBus = commandBus;
    }

    @GetMapping
    public ResponseEntity<SuccessResponseDTO> getElectricityRates() {
        List<ElectricityRate> rates = queryBus.dispatch(new GetElectricityRatesQuery());
        List<ElectricityRateResponse> data = rates.stream().map(ElectricityRateResponse::from).toList();
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Tarifas de mercado obtenidas correctamente", data));
    }

    /** Admin-only on the handler itself, not just on its path — see {@code KnowledgeAdminController}. */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponseDTO> deleteAllElectricityRates() {
        commandBus.dispatch(new DeleteAllElectricityRatesCommand());
        return ResponseEntity.ok(SuccessResponseDTO.of(200, "Todas las tarifas de mercado han sido eliminadas"));
    }
}