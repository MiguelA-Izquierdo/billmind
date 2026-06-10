package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.izquierdo.billmind._shared.infrastructure.llm.LlmResponseJsonSanitizer;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceFieldExtractionException;
import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor.ExtractionPromptBuilder;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor.InvoiceFieldsValidator;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmInvoiceFieldExtractorTest {

    @Mock private ChatModel               chatModel;
    @Mock private ExtractionPromptBuilder promptBuilder;
    @Mock private InvoiceFieldsValidator  validator;

    private LlmInvoiceFieldExtractor extractor;

    private static final String ELECTRICITY_JSON =
            "{\"billingPeriodStart\":\"2024-01-01\",\"billingPeriodEnd\":\"2024-01-31\"," +
            "\"totalAmount\":67.20,\"consumptionKwh\":320.0,\"pricePerKwh\":0.1823,\"contractedPowerKw\":3.3}";

    private static final String GAS_JSON =
            "{\"billingPeriodStart\":\"2024-02-01\",\"billingPeriodEnd\":\"2024-02-28\"," +
            "\"totalAmount\":89.34,\"consumptionM3\":87.0,\"consumptionKwh\":984.0,\"pricePerKwh\":0.0712}";

    private static final String WATER_JSON =
            "{\"billingPeriodStart\":\"2024-03-01\",\"billingPeriodEnd\":\"2024-05-31\"," +
            "\"totalAmount\":39.21,\"consumptionM3\":18.0,\"pricePerM3\":0.8234,\"sewageCharge\":12.40}";

    private static final String TELECOM_JSON =
            "{\"billingPeriodStart\":\"2024-04-01\",\"billingPeriodEnd\":\"2024-04-30\"," +
            "\"totalAmount\":37.93,\"contractedSpeedMbps\":600,\"mobileDataGb\":null," +
            "\"includedMobileLines\":1,\"mobileLineCount\":3,\"monthlyFee\":34.90," +
            "\"lines\":[" +
            "{\"lineType\":\"FIBRA\",\"planName\":\"FIBRA 600Mb + LA SINFÍN GB ILIMITADOS\",\"baseAmount\":60.33,\"discount\":-33.22}," +
            "{\"lineType\":\"MOVIL\",\"planName\":\"LA DUO ADICIONAL\",\"baseAmount\":7.44,\"discount\":-2.48}," +
            "{\"lineType\":\"MOVIL\",\"planName\":\"LA DUO PRINCIPAL\",\"baseAmount\":5.79,\"discount\":-4.96}," +
            "{\"lineType\":\"DISPOSITIVO\",\"planName\":\"CUOTA MENSUAL (22/24) - XIAOMI SMART AIR FRYER 6,5L\",\"baseAmount\":2.50,\"discount\":0.00}" +
            "]," +
            "\"streamingServices\":[" +
            "{\"platform\":\"NETFLIX\",\"tier\":\"CON ANUNCIOS\"}," +
            "{\"platform\":\"DISNEY_PLUS\",\"tier\":\"CON ANUNCIOS\"}" +
            "]}";

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        extractor = new LlmInvoiceFieldExtractor(
                chatModel,
                objectMapper,
                promptBuilder,
                new LlmResponseJsonSanitizer(),
                validator
        );
        lenient().when(promptBuilder.build(anyString(), anyString())).thenReturn("prompt");
    }

    // ── Dispatch por tipo ─────────────────────────────────────────────────────

    @Test
    void shouldExtractElectricityFields() {
        doReturn(ELECTRICITY_JSON).when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.LUZ);

        assertThat(result).isInstanceOf(ElectricityFields.class);
        ElectricityFields fields = (ElectricityFields) result;
        assertThat(fields.billingPeriodStart()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(fields.billingPeriodEnd()).isEqualTo(LocalDate.of(2024, 1, 31));
        assertThat(fields.totalAmount()).isEqualByComparingTo("67.20");
        assertThat(fields.consumptionKwh()).isEqualByComparingTo("320.0");
    }

    @Test
    void shouldExtractGasFields() {
        doReturn(GAS_JSON).when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.GAS);

        assertThat(result).isInstanceOf(GasFields.class);
        GasFields fields = (GasFields) result;
        assertThat(fields.consumptionM3()).isEqualByComparingTo("87.0");
        assertThat(fields.consumptionKwh()).isEqualByComparingTo("984.0");
    }

    @Test
    void shouldExtractWaterFields() {
        doReturn(WATER_JSON).when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.AGUA);

        assertThat(result).isInstanceOf(WaterFields.class);
        WaterFields fields = (WaterFields) result;
        assertThat(fields.consumptionM3()).isEqualByComparingTo("18.0");
        assertThat(fields.sewageCharge()).isEqualByComparingTo("12.40");
    }

    @Test
    void shouldExtractTelecomFields() {
        doReturn(TELECOM_JSON).when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.TELCO);

        assertThat(result).isInstanceOf(TelecomFields.class);
        TelecomFields fields = (TelecomFields) result;
        assertThat(fields.contractedSpeedMbps()).isEqualTo(600);
        assertThat(fields.includedMobileLines()).isEqualTo(1);
        assertThat(fields.mobileLineCount()).isEqualTo(3);
        assertThat(fields.lines()).hasSize(4);
        assertThat(fields.lines().get(0).lineType()).isEqualTo("FIBRA");
        assertThat(fields.lines().get(3).lineType()).isEqualTo("DISPOSITIVO");
        assertThat(fields.streamingServices()).hasSize(2);
        assertThat(fields.streamingServices().get(0).platform()).isEqualTo("NETFLIX");
    }

    @Test
    void shouldThrowForUnsupportedType() {
        assertThatThrownBy(() -> extractor.extract("texto", InvoiceType.OTRO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Sanitización JSON ─────────────────────────────────────────────────────

    @Test
    void shouldParseResponseWrappedInMarkdownFence() {
        doReturn("```json\n" + ELECTRICITY_JSON + "\n```").when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.LUZ);

        assertThat(result).isInstanceOf(ElectricityFields.class);
    }

    @Test
    void shouldParseResponseWithLeadingProse() {
        doReturn("Aquí tienes los campos: " + ELECTRICITY_JSON).when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.LUZ);

        assertThat(result).isInstanceOf(ElectricityFields.class);
    }

    // ── Retry con reparación ──────────────────────────────────────────────────

    @Test
    void shouldRetryWithRepairWhenFirstResponseIsInvalidJson() {
        doReturn("invalid json response")
                .doReturn(ELECTRICITY_JSON)
                .when(chatModel).chat(anyString());

        InvoiceFields result = extractor.extract("texto factura", InvoiceType.LUZ);

        assertThat(result).isInstanceOf(ElectricityFields.class);
        verify(chatModel, times(2)).chat(anyString());
    }

    @Test
    void shouldThrowAfterBothParseAttemptsFail() {
        doReturn("invalid json")
                .doReturn("still invalid json")
                .when(chatModel).chat(anyString());

        assertThatThrownBy(() -> extractor.extract("texto factura", InvoiceType.LUZ))
                .isInstanceOf(InvoiceFieldExtractionException.class);

        verify(chatModel, times(2)).chat(anyString());
    }

    // ── Integración con validador ─────────────────────────────────────────────

    @Test
    void shouldCallValidatorAfterSuccessfulParse() {
        doReturn(ELECTRICITY_JSON).when(chatModel).chat(anyString());

        extractor.extract("texto factura", InvoiceType.LUZ);

        verify(validator).validate(any(ElectricityFields.class));
    }

    @Test
    void shouldPropagateValidationFailureWithoutRetry() {
        doReturn(ELECTRICITY_JSON).when(chatModel).chat(anyString());
        doThrow(new InvoiceFieldExtractionException(new RuntimeException("invalid dates")))
                .when(validator).validate(any());

        assertThatThrownBy(() -> extractor.extract("texto factura", InvoiceType.LUZ))
                .isInstanceOf(InvoiceFieldExtractionException.class);

        verify(chatModel, times(1)).chat(anyString());
    }
}