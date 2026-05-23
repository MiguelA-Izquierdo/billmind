package dev.izquierdo.billmind.market.application.command;

import dev.izquierdo.billmind._shared.application.command.Command;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record SaveElectricityRateCommand(
        UUID id,
        InvoiceType supplyType,
        String company,
        String tariffName,
        BigDecimal pricePerKwh,
        BigDecimal pricePerKwhValle,
        BigDecimal pricePerKwhLlano,
        BigDecimal pricePerKwhPunta,
        BigDecimal contractedPowerPrice,
        BigDecimal contractedPowerPriceP2,
        LocalDate validFrom,
        LocalDate validTo,
        String region,
        String source,
        Instant receivedAt
) implements Command {

    public SaveElectricityRateCommand {
        Objects.requireNonNull(id,         "id cannot be null");
        Objects.requireNonNull(supplyType, "supplyType cannot be null");
        Objects.requireNonNull(company,    "company cannot be null");
        Objects.requireNonNull(tariffName, "tariffName cannot be null");
        Objects.requireNonNull(validFrom,  "validFrom cannot be null");
        Objects.requireNonNull(source,     "source cannot be null");
    }
}