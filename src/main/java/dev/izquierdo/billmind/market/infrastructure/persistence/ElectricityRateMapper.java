package dev.izquierdo.billmind.market.infrastructure.persistence;

import dev.izquierdo.billmind.market.domain.model.ElectricityRate;

class ElectricityRateMapper {

    static ElectricityRateEntity toEntity(ElectricityRate rate) {
        ElectricityRateEntity entity = new ElectricityRateEntity();
        entity.id                   = rate.getId();
        entity.supplyType           = rate.getSupplyType();
        entity.company              = rate.getCompany();
        entity.tariffName           = rate.getTariffName();
        entity.pricePerKwh          = rate.getPricePerKwh();
        entity.pricePerKwhValle     = rate.getPricePerKwhValle();
        entity.pricePerKwhLlano     = rate.getPricePerKwhLlano();
        entity.pricePerKwhPunta     = rate.getPricePerKwhPunta();
        entity.contractedPowerPrice = rate.getContractedPowerPrice();
        entity.contractedPowerPriceP2 = rate.getContractedPowerPriceP2();
        entity.validFrom              = rate.getValidFrom();
        entity.validTo              = rate.getValidTo();
        entity.region               = rate.getRegion();
        entity.source               = rate.getSource();
        entity.receivedAt           = rate.getReceivedAt();
        return entity;
    }

    static ElectricityRate toDomain(ElectricityRateEntity entity) {
        return ElectricityRate.builder(entity.id)
            .supplyType(entity.supplyType)
            .company(entity.company)
            .tariffName(entity.tariffName)
            .pricePerKwh(entity.pricePerKwh)
            .pricePerKwhValle(entity.pricePerKwhValle)
            .pricePerKwhLlano(entity.pricePerKwhLlano)
            .pricePerKwhPunta(entity.pricePerKwhPunta)
            .contractedPowerPrice(entity.contractedPowerPrice)
            .contractedPowerPriceP2(entity.contractedPowerPriceP2)
            .validFrom(entity.validFrom)
            .validTo(entity.validTo)
            .region(entity.region)
            .source(entity.source)
            .receivedAt(entity.receivedAt)
            .build();
    }
}