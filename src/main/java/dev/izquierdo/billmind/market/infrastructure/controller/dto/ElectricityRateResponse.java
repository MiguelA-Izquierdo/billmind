package dev.izquierdo.billmind.market.infrastructure.controller.dto;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ElectricityRateResponse(
        UUID id,
        String supplyType,
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
) {
    public static ElectricityRateResponse from(ElectricityRate rate) {
        return new ElectricityRateResponse(
                rate.getId(),
                rate.getSupplyType().name(),
                rate.getCompany(),
                rate.getTariffName(),
                rate.getPricePerKwh(),
                rate.getPricePerKwhValle(),
                rate.getPricePerKwhLlano(),
                rate.getPricePerKwhPunta(),
                rate.getContractedPowerPrice(),
                rate.getContractedPowerPriceP2(),
                rate.getValidFrom(),
                rate.getValidTo(),
                rate.getRegion(),
                rate.getSource(),
                rate.getReceivedAt()
        );
    }
}