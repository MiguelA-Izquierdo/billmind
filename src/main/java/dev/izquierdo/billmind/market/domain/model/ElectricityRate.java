package dev.izquierdo.billmind.market.domain.model;

import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind.market.domain.exceptions.InvalidElectricityRateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class ElectricityRate {

    private final UUID id;
    private final SupplyDomain supplyType;
    private final String company;
    private final String tariffName;
    private final BigDecimal pricePerKwh;
    private final BigDecimal pricePerKwhValle;
    private final BigDecimal pricePerKwhLlano;
    private final BigDecimal pricePerKwhPunta;
    private final BigDecimal contractedPowerPrice;
    private final BigDecimal contractedPowerPriceP2;
    private final LocalDate validFrom;
    private final LocalDate validTo;
    private final String region;
    private final String source;
    private final Instant receivedAt;

    private ElectricityRate(Builder builder) {
        this.id                     = Objects.requireNonNull(builder.id,         "id cannot be null");
        this.supplyType             = Objects.requireNonNull(builder.supplyType, "supplyType cannot be null");
        this.company                = Objects.requireNonNull(builder.company,    "company cannot be null");
        this.tariffName             = Objects.requireNonNull(builder.tariffName, "tariffName cannot be null");
        this.pricePerKwh            = builder.pricePerKwh;
        this.pricePerKwhValle       = builder.pricePerKwhValle;
        this.pricePerKwhLlano       = builder.pricePerKwhLlano;
        this.pricePerKwhPunta       = builder.pricePerKwhPunta;
        this.contractedPowerPrice   = builder.contractedPowerPrice;
        this.contractedPowerPriceP2 = builder.contractedPowerPriceP2;
        this.validFrom              = Objects.requireNonNull(builder.validFrom,  "validFrom cannot be null");
        this.validTo                = builder.validTo;
        this.region                 = builder.region;
        this.source                 = Objects.requireNonNull(builder.source,     "source cannot be null");
        this.receivedAt             = builder.receivedAt != null ? builder.receivedAt : Instant.now();

        if (this.company.isBlank())
            throw new InvalidElectricityRateException("company cannot be blank");
        if (this.tariffName.isBlank())
            throw new InvalidElectricityRateException("tariffName cannot be blank");
        if (this.source.isBlank())
            throw new InvalidElectricityRateException("source cannot be blank");
        if (this.pricePerKwh == null && this.pricePerKwhValle == null)
            throw new InvalidElectricityRateException("either pricePerKwh or pricePerKwhValle must be provided");
        if (this.pricePerKwh != null && this.pricePerKwh.signum() < 0)
            throw new InvalidElectricityRateException("pricePerKwh must be non-negative: " + this.pricePerKwh);
        if (this.validTo != null && this.validTo.isBefore(this.validFrom))
            throw new InvalidElectricityRateException("validTo cannot be before validFrom");
    }

    public static Builder builder(UUID id) {
        return new Builder(id);
    }

    public UUID getId()                           { return id; }
    public SupplyDomain getSupplyType()            { return supplyType; }
    public String getCompany()                    { return company; }
    public String getTariffName()                 { return tariffName; }
    public BigDecimal getPricePerKwh()            { return pricePerKwh; }
    public BigDecimal getPricePerKwhValle()       { return pricePerKwhValle; }
    public BigDecimal getPricePerKwhLlano()       { return pricePerKwhLlano; }
    public BigDecimal getPricePerKwhPunta()       { return pricePerKwhPunta; }
    public BigDecimal getContractedPowerPrice()   { return contractedPowerPrice; }
    public BigDecimal getContractedPowerPriceP2() { return contractedPowerPriceP2; }
    public LocalDate getValidFrom()               { return validFrom; }
    public LocalDate getValidTo()                 { return validTo; }
    public String getRegion()                     { return region; }
    public String getSource()                     { return source; }
    public Instant getReceivedAt()                { return receivedAt; }

    public static final class Builder {
        private final UUID id;
        private SupplyDomain supplyType;
        private String company;
        private String tariffName;
        private BigDecimal pricePerKwh;
        private BigDecimal pricePerKwhValle;
        private BigDecimal pricePerKwhLlano;
        private BigDecimal pricePerKwhPunta;
        private BigDecimal contractedPowerPrice;
        private BigDecimal contractedPowerPriceP2;
        private LocalDate validFrom;
        private LocalDate validTo;
        private String region;
        private String source;
        private Instant receivedAt;

        private Builder(UUID id) { this.id = id; }

        public Builder supplyType(SupplyDomain v)          { this.supplyType           = v; return this; }
        public Builder company(String v)                  { this.company              = v; return this; }
        public Builder tariffName(String v)               { this.tariffName           = v; return this; }
        public Builder pricePerKwh(BigDecimal v)          { this.pricePerKwh          = v; return this; }
        public Builder pricePerKwhValle(BigDecimal v)     { this.pricePerKwhValle     = v; return this; }
        public Builder pricePerKwhLlano(BigDecimal v)     { this.pricePerKwhLlano     = v; return this; }
        public Builder pricePerKwhPunta(BigDecimal v)     { this.pricePerKwhPunta     = v; return this; }
        public Builder contractedPowerPrice(BigDecimal v) { this.contractedPowerPrice   = v; return this; }
        public Builder contractedPowerPriceP2(BigDecimal v){ this.contractedPowerPriceP2 = v; return this; }
        public Builder validFrom(LocalDate v)             { this.validFrom            = v; return this; }
        public Builder validTo(LocalDate v)               { this.validTo              = v; return this; }
        public Builder region(String v)                   { this.region               = v; return this; }
        public Builder source(String v)                   { this.source               = v; return this; }
        public Builder receivedAt(Instant v)              { this.receivedAt           = v; return this; }
        public ElectricityRate build()                         { return new ElectricityRate(this); }
    }

    @Override
    public String toString() {
        return "ElectricityRate{" +
                "id=" + id +
                ", supplyType=" + supplyType +
                ", company='" + company + '\'' +
                ", tariffName='" + tariffName + '\'' +
                ", pricePerKwh=" + pricePerKwh +
                ", pricePerKwhValle=" + pricePerKwhValle +
                ", pricePerKwhLlano=" + pricePerKwhLlano +
                ", pricePerKwhPunta=" + pricePerKwhPunta +
                ", contractedPowerPrice=" + contractedPowerPrice +
                ", contractedPowerPriceP2=" + contractedPowerPriceP2 +
                ", validFrom=" + validFrom +
                ", validTo=" + validTo +
                ", region='" + region + '\'' +
                ", source='" + source + '\'' +
                ", receivedAt=" + receivedAt +
                '}';
    }
}