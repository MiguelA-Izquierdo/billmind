package dev.izquierdo.billmind.market.infrastructure.persistence;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "electricity_rates")
class ElectricityRateEntity {

    @Id
    @Column(updatable = false, nullable = false)
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_type", nullable = false, length = 10)
    InvoiceType supplyType;

    @Column(name = "company", nullable = false)
    String company;

    @Column(name = "tariff_name", nullable = false)
    String tariffName;

    @Column(name = "price_per_kwh", precision = 10, scale = 6)
    BigDecimal pricePerKwh;

    @Column(name = "price_per_kwh_valle", precision = 10, scale = 6)
    BigDecimal pricePerKwhValle;

    @Column(name = "price_per_kwh_llano", precision = 10, scale = 6)
    BigDecimal pricePerKwhLlano;

    @Column(name = "price_per_kwh_punta", precision = 10, scale = 6)
    BigDecimal pricePerKwhPunta;

    @Column(name = "contracted_power_price", precision = 10, scale = 6)
    BigDecimal contractedPowerPrice;

    @Column(name = "contracted_power_price_p2", precision = 10, scale = 6)
    BigDecimal contractedPowerPriceP2;

    @Column(name = "valid_from", nullable = false)
    LocalDate validFrom;

    @Column(name = "valid_to")
    LocalDate validTo;

    @Column(name = "region")
    String region;

    @Column(name = "source", nullable = false)
    String source;

    @Column(name = "received_at", nullable = false, updatable = false)
    Instant receivedAt;

    protected ElectricityRateEntity() {}
}