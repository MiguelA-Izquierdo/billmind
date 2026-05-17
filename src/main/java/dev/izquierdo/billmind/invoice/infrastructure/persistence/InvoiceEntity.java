package dev.izquierdo.billmind.invoice.infrastructure.persistence;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind.invoice.domain.model.InvoiceType;
import dev.izquierdo.billmind.invoice.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind.invoice.domain.model.fields.GasFields;
import dev.izquierdo.billmind.invoice.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.invoice.domain.model.fields.MobileLine;
import dev.izquierdo.billmind.invoice.domain.model.fields.StreamingService;
import dev.izquierdo.billmind.invoice.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind.invoice.domain.model.fields.WaterFields;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class InvoiceEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", updatable = false)
    private UUID sessionId;

    @Column(name = "file_name", nullable = false, updatable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "supply_type", nullable = false, length = 10)
    private InvoiceType supplyType;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    // ── Common extracted fields ───────────────────────────────────────────────
    @Column(name = "billing_period_start")
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end")
    private LocalDate billingPeriodEnd;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "raw_text_redacted", columnDefinition = "TEXT")
    private String rawTextRedacted;

    // ── LUZ ───────────────────────────────────────────────────────────────────
    @Column(name = "consumption_kwh", precision = 10, scale = 3)
    private BigDecimal consumptionKwh;

    @Column(name = "price_per_kwh", precision = 10, scale = 6)
    private BigDecimal pricePerKwh;

    @Column(name = "contracted_power_kw", precision = 6, scale = 3)
    private BigDecimal contractedPowerKw;

    // ── GAS + AGUA ────────────────────────────────────────────────────────────
    @Column(name = "consumption_m3", precision = 10, scale = 3)
    private BigDecimal consumptionM3;

    @Column(name = "price_per_m3", precision = 10, scale = 6)
    private BigDecimal pricePerM3;

    // ── AGUA ──────────────────────────────────────────────────────────────────
    @Column(name = "sewage_charge", precision = 10, scale = 2)
    private BigDecimal sewageCharge;

    // ── TELCO ─────────────────────────────────────────────────────────────────
    @Column(name = "contracted_speed_mbps")
    private Integer contractedSpeedMbps;

    @Column(name = "mobile_data_gb")
    private Integer mobileDataGb;

    @Column(name = "phone_lines")
    private Integer phoneLines;

    @Convert(converter = MobileLineListConverter.class)
    @Column(name = "mobile_lines_json", columnDefinition = "TEXT")
    private List<MobileLine> mobileLines;

    @Column(name = "included_mobile_lines")
    private Integer includedMobileLines;

    @Column(name = "mobile_line_count")
    private Integer mobileLineCount;

    @Convert(converter = StreamingServiceListConverter.class)
    @Column(name = "streaming_services_json", columnDefinition = "TEXT")
    private List<StreamingService> streamingServices;

    @Column(name = "monthly_fee", precision = 10, scale = 2)
    private BigDecimal monthlyFee;

    protected InvoiceEntity() {}

    public static InvoiceEntity from(Invoice invoice) {
        if (invoice.getSupplyType() == InvoiceType.OTRO) {
            throw new IllegalStateException("Cannot persist invoice with type OTRO: id=" + invoice.getId());
        }
        InvoiceEntity entity = new InvoiceEntity();
        entity.id              = invoice.getId();
        entity.sessionId       = invoice.getSessionId();
        entity.fileName        = invoice.getFileName();
        entity.supplyType      = invoice.getSupplyType();
        entity.provider        = invoice.getProvider();
        entity.uploadedAt      = invoice.getUploadedAt();
        entity.rawTextRedacted = invoice.getRawTextRedacted();

        InvoiceFields fields = invoice.getFields();
        if (fields != null) {
            entity.billingPeriodStart = fields.billingPeriodStart();
            entity.billingPeriodEnd   = fields.billingPeriodEnd();
            entity.totalAmount        = fields.totalAmount();
            switch (fields) {
                case ElectricityFields f -> {
                    entity.consumptionKwh  = f.consumptionKwh();
                    entity.pricePerKwh     = f.pricePerKwh();
                    entity.contractedPowerKw = f.contractedPowerKw();
                }
                case GasFields f -> {
                    entity.consumptionM3  = f.consumptionM3();
                    entity.consumptionKwh = f.consumptionKwh();
                    entity.pricePerKwh    = f.pricePerKwh();
                }
                case WaterFields f -> {
                    entity.consumptionM3 = f.consumptionM3();
                    entity.pricePerM3    = f.pricePerM3();
                    entity.sewageCharge  = f.sewageCharge();
                }
                case TelecomFields f -> {
                    entity.contractedSpeedMbps = f.contractedSpeedMbps();
                    entity.mobileDataGb        = f.mobileDataGb();
                    entity.mobileLines          = f.lines();
                    entity.phoneLines           = f.lines() != null ? f.lines().size() : null;
                    entity.includedMobileLines  = f.includedMobileLines();
                    entity.mobileLineCount      = f.mobileLineCount();
                    entity.streamingServices   = f.streamingServices();
                    entity.monthlyFee          = f.monthlyFee();
                }
            }
        }
        return entity;
    }

    public Invoice toDomain() {
        return Invoice.builder(id, fileName)
            .uploadedAt(uploadedAt)
            .supplyType(supplyType)
            .provider(provider)
            .sessionId(sessionId)
            .fields(buildFields())
            .rawTextRedacted(rawTextRedacted)
            .build();
    }

    private InvoiceFields buildFields() {
        if (billingPeriodStart == null) return null;
        return switch (supplyType) {
            case LUZ   -> new ElectricityFields(billingPeriodStart, billingPeriodEnd, totalAmount,
                                                consumptionKwh, pricePerKwh, contractedPowerKw);
            case GAS   -> new GasFields(billingPeriodStart, billingPeriodEnd, totalAmount,
                                        consumptionM3, consumptionKwh, pricePerKwh);
            case AGUA  -> new WaterFields(billingPeriodStart, billingPeriodEnd, totalAmount,
                                          consumptionM3, pricePerM3, sewageCharge);
            case TELCO -> new TelecomFields(billingPeriodStart, billingPeriodEnd, totalAmount,
                                            contractedSpeedMbps, mobileDataGb, includedMobileLines,
                                            mobileLineCount, mobileLines, streamingServices, monthlyFee);
            case OTRO  -> null;
        };
    }

    public UUID getId()                { return id; }
    public UUID getSessionId()         { return sessionId; }
    public String getFileName()        { return fileName; }
    public InvoiceType getSupplyType() { return supplyType; }
    public String getProvider()        { return provider; }
    public Instant getUploadedAt()     { return uploadedAt; }
}