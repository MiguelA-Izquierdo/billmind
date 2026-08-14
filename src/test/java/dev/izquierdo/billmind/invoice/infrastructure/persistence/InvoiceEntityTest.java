package dev.izquierdo.billmind.invoice.infrastructure.persistence;

import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.MobileLine;
import dev.izquierdo.billmind._shared.domain.model.fields.StreamingService;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceEntityTest {

    @Test
    void shouldMapAllFieldsFromDomain() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Invoice invoice = Invoice.builder(id, "factura.pdf")
            .supplyType(SupplyDomain.ELECTRICITY)
            .provider("IBERDROLA")
            .sessionId(sessionId)
            .build();

        InvoiceEntity entity = InvoiceEntity.from(invoice);

        assertThat(entity.getId()).isEqualTo(id);
        assertThat(entity.getFileName()).isEqualTo("factura.pdf");
        assertThat(entity.getSupplyType()).isEqualTo(SupplyDomain.ELECTRICITY);
        assertThat(entity.getProvider()).isEqualTo("IBERDROLA");
        assertThat(entity.getSessionId()).isEqualTo(sessionId);
        assertThat(entity.getUploadedAt()).isNotNull();
    }

    @Test
    void shouldRoundtripToDomain() {
        UUID id = UUID.randomUUID();
        Invoice original = Invoice.builder(id, "factura_gas.pdf")
            .supplyType(SupplyDomain.GAS)
            .provider("NATURGY")
            .sessionId(UUID.randomUUID())
            .build();

        Invoice reconstructed = InvoiceEntity.from(original).toDomain();

        assertThat(reconstructed.getId()).isEqualTo(original.getId());
        assertThat(reconstructed.getFileName()).isEqualTo(original.getFileName());
        assertThat(reconstructed.getSupplyType()).isEqualTo(original.getSupplyType());
        assertThat(reconstructed.getProvider()).isEqualTo(original.getProvider());
    }

    @Test
    void shouldHandleNullSessionId() {
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf")
            .supplyType(SupplyDomain.WATER)
            .provider("AGUAS")
            .build();

        InvoiceEntity entity = InvoiceEntity.from(invoice);

        assertThat(entity.getSessionId()).isNull();
    }

    @Test
    void shouldReturnNullFieldsWhenNotYetExtracted() {
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura.pdf")
            .supplyType(SupplyDomain.ELECTRICITY)
            .build();

        Invoice reconstructed = InvoiceEntity.from(invoice).toDomain();

        assertThat(reconstructed.getFields()).isNull();
        assertThat(reconstructed.getRawTextRedacted()).isNull();
    }

    @Test
    void shouldRoundtripElectricityFields() {
        ElectricityFields fields = new ElectricityFields(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31),
                new BigDecimal("45.50"), new BigDecimal("405.000"), null, null, null,
                new BigDecimal("0.140000"), null, null, null, new BigDecimal("3.300"), null, null);
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura_luz.pdf")
                .supplyType(SupplyDomain.ELECTRICITY)
                .fields(fields)
                .build();

        Invoice reconstructed = InvoiceEntity.from(invoice).toDomain();

        assertThat(reconstructed.getFields()).isInstanceOf(ElectricityFields.class);
        ElectricityFields result = (ElectricityFields) reconstructed.getFields();
        assertThat(result.consumptionKwh()).isEqualByComparingTo("405.000");
        assertThat(result.pricePerKwh()).isEqualByComparingTo("0.140000");
        assertThat(result.contractedPowerKw()).isEqualByComparingTo("3.300");
    }

    @Test
    void shouldRoundtripGasFields() {
        GasFields fields = new GasFields(
                LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29),
                new BigDecimal("62.80"), new BigDecimal("120.000"),
                new BigDecimal("139.500"), new BigDecimal("0.065000"));
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura_gas.pdf")
                .supplyType(SupplyDomain.GAS)
                .fields(fields)
                .build();

        Invoice reconstructed = InvoiceEntity.from(invoice).toDomain();

        assertThat(reconstructed.getFields()).isInstanceOf(GasFields.class);
        GasFields result = (GasFields) reconstructed.getFields();
        assertThat(result.consumptionM3()).isEqualByComparingTo("120.000");
        assertThat(result.consumptionKwh()).isEqualByComparingTo("139.500");
    }

    @Test
    void shouldRoundtripWaterFields() {
        WaterFields fields = new WaterFields(
                LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 31),
                new BigDecimal("28.40"), new BigDecimal("18.500"),
                new BigDecimal("1.250000"), new BigDecimal("5.20"));
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura_agua.pdf")
                .supplyType(SupplyDomain.WATER)
                .fields(fields)
                .build();

        Invoice reconstructed = InvoiceEntity.from(invoice).toDomain();

        assertThat(reconstructed.getFields()).isInstanceOf(WaterFields.class);
        WaterFields result = (WaterFields) reconstructed.getFields();
        assertThat(result.consumptionM3()).isEqualByComparingTo("18.500");
        assertThat(result.sewageCharge()).isEqualByComparingTo("5.20");
    }

    @Test
    void shouldRoundtripTelecomFields() {
        List<MobileLine> lines = List.of(
                new MobileLine("FIBRA", "FIBRA 600Mb + LA SINFÍN GB ILIMITADOS", new BigDecimal("60.33"), new BigDecimal("-33.22")),
                new MobileLine("MOVIL", "LA DUO ADICIONAL", new BigDecimal("7.44"), new BigDecimal("-2.48")),
                new MobileLine("MOVIL", "LA DUO PRINCIPAL", new BigDecimal("5.79"), new BigDecimal("-4.96"))
        );
        TelecomFields fields = new TelecomFields(
                LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30),
                new BigDecimal("39.80"), 600, null, 1, 3, lines,
                List.of(new StreamingService("NETFLIX", "CON ANUNCIOS")),
                new BigDecimal("39.80"));
        Invoice invoice = Invoice.builder(UUID.randomUUID(), "factura_telco.pdf")
                .supplyType(SupplyDomain.TELECOM)
                .fields(fields)
                .build();

        Invoice reconstructed = InvoiceEntity.from(invoice).toDomain();

        assertThat(reconstructed.getFields()).isInstanceOf(TelecomFields.class);
        TelecomFields result = (TelecomFields) reconstructed.getFields();
        assertThat(result.contractedSpeedMbps()).isEqualTo(600);
        assertThat(result.includedMobileLines()).isEqualTo(1);
        assertThat(result.mobileLineCount()).isEqualTo(3);
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines().getFirst().lineType()).isEqualTo("FIBRA");
        assertThat(result.streamingServices()).hasSize(1);
        assertThat(result.streamingServices().getFirst().platform()).isEqualTo("NETFLIX");
    }
}