package dev.izquierdo.billmind.invoice.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.izquierdo.billmind._shared.infrastructure.llm.LlmResponseJsonSanitizer;
import dev.izquierdo.billmind._shared.infrastructure.llm.TimedChatLanguageModel;
import dev.izquierdo.billmind._shared.infrastructure.llm.prompt.PromptFence;
import dev.izquierdo.billmind.invoice.domain.exceptions.InvoiceFieldExtractionException;
import dev.izquierdo.billmind._shared.domain.model.SupplyDomain;
import dev.izquierdo.billmind._shared.domain.model.fields.ElectricityFields;
import dev.izquierdo.billmind._shared.domain.model.fields.GasFields;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind._shared.domain.model.fields.TelecomFields;
import dev.izquierdo.billmind._shared.domain.model.fields.WaterFields;
import dev.izquierdo.billmind.invoice.domain.port.InvoiceFieldExtractor;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor.ExtractionPromptBuilder;
import dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor.InvoiceFieldsValidator;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LlmInvoiceFieldExtractor implements InvoiceFieldExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmInvoiceFieldExtractor.class);

    // Instruction blocks only — invoice text is injected separately by ExtractionPromptBuilder.

    private static final String ELECTRICITY_INSTRUCTIONS =
            "Extract fields from the Spanish electricity invoice delimited below.\n" +
            "Output ONLY valid JSON matching this schema (no prose, no markdown fences):\n" +
            "{\"billingPeriodStart\":\"YYYY-MM-DD\",\"billingPeriodEnd\":\"YYYY-MM-DD\"," +
            "\"totalAmount\":0.00,\"consumptionKwh\":0.0," +
            "\"consumptionKwhP1\":null,\"consumptionKwhP2\":null,\"consumptionKwhP3\":null," +
            "\"pricePerKwh\":0.000,\"pricePerKwhP1\":null,\"pricePerKwhP2\":null,\"pricePerKwhP3\":null," +
            "\"contractedPowerKw\":0.0}\n" +
            "ISO-8601 dates. Dot decimal separator. Null for any missing field.\n\n" +
            "=== pricePerKwh / TOU RULES ===\n" +
            "TOU (discriminación horaria) means the PRICE PER kWh DIFFERS by period. If prices vary: set pricePerKwhP1/P2/P3, leave pricePerKwh null.\n" +
            "  P1 = punta  (labels: P1, PUNTA, PEAK)\n" +
            "  P2 = llano  (labels: P2, LLANO, FLAT)\n" +
            "  P3 = valle  (labels: P3, VALLE, OFF-PEAK, NOCHE)\n" +
            "  Two-period (día/noche): use P1=día, P3=noche, P2=null.\n" +
            "FLAT RATE: the same price applies to ALL periods (or only one price appears). Set pricePerKwh, leave pricePerKwhP1/P2/P3 null.\n" +
            "  Labels: '€/kWh', 'precio energía', 'término de energía activa', 'coste de la energía'.\n" +
            "CRITICAL: if P1/P2/P3 prices are all equal (or one single price is multiplied across periods), treat as FLAT RATE — set pricePerKwh, leave P1/P2/P3 null.\n" +
            "Never set both pricePerKwh and any pricePerKwhP1/P2/P3.\n\n" +
            "=== LAST RESORT: EFFECTIVE AVERAGE PRICE ===\n" +
            "Some tariffs split consumption by commercial period names that map to none of the labels above\n" +
            "('Horas Happy', 'Horas Gratis', 'Resto de horas', 'Horas Ahorro', 'Tempo'). Never force them into\n" +
            "P1/P2/P3, and never return every price null. Derive the effective average instead:\n" +
            "  pricePerKwh = (euros charged for energy) / (total kWh consumed)\n" +
            "Use the 'Energía' subtotal ONLY — never the invoice total, which also carries power, taxes,\n" +
            "meter rental and levies. Round to 6 decimals, set pricePerKwh, leave pricePerKwhP1/P2/P3 null.\n" +
            "Apply the same rule when no unit price appears anywhere but an energy subtotal and kWh do.\n\n" +
            "=== consumptionKwh ===\n" +
            "Total consumption. Labels: 'kWh', 'Energía consumida'. For TOU: sum all periods.\n\n" +
            "=== consumptionKwhP1/P2/P3 ===\n" +
            "Set to the kWh consumed in each period when shown, for both flat-rate and TOU invoices.\n" +
            "  P1=punta, P2=llano, P3=valle. Two-period: P1=día, P3=noche, P2=null.\n" +
            "  Leave null when the invoice does not break down consumption by period.\n\n" +
            "Input: IBERDROLA Periodo 01/01/2024 al 31/01/2024 320 kWh 0,1823 €/kWh 3,3 kW Total 67,20 €\n" +
            "Output: {\"billingPeriodStart\":\"2024-01-01\",\"billingPeriodEnd\":\"2024-01-31\"," +
            "\"totalAmount\":67.20,\"consumptionKwh\":320.0," +
            "\"consumptionKwhP1\":null,\"consumptionKwhP2\":null,\"consumptionKwhP3\":null," +
            "\"pricePerKwh\":0.1823," +
            "\"pricePerKwhP1\":null,\"pricePerKwhP2\":null,\"pricePerKwhP3\":null,\"contractedPowerKw\":3.3}\n\n" +
            "Input: ENDESA 08/05/2018 10/06/2018 | P1 (punta) 120 kWh 0,15234 €/kWh | P2 (llano) 130 kWh 0,10123 €/kWh | P3 (valle) 100 kWh 0,06891 €/kWh | Potencia 3,45 kW | Total 80,95 €\n" +
            "Output: {\"billingPeriodStart\":\"2018-05-08\",\"billingPeriodEnd\":\"2018-06-10\"," +
            "\"totalAmount\":80.95,\"consumptionKwh\":350.0," +
            "\"consumptionKwhP1\":120.0,\"consumptionKwhP2\":130.0,\"consumptionKwhP3\":100.0," +
            "\"pricePerKwh\":null," +
            "\"pricePerKwhP1\":0.15234,\"pricePerKwhP2\":0.10123,\"pricePerKwhP3\":0.06891,\"contractedPowerKw\":3.45}\n\n" +
            // Commercial period names ("Horas Happy" / "Resto de horas") map to no P1/P2/P3 label:
            // 12,46 € of energy over 64,257 kWh → 0.193909 €/kWh, free hours already priced in.
            "Input: ENDESA Tempo Happy | Periodo de facturación: del 31/12/2023 a 31/01/2024 | Consumo total 64,257 kWh | " +
            "Energía 12,46 € | Horas Happy de mayor consumo 11,456 kWh x 0 Eur/kWh 0,00 € | " +
            "Resto de horas 52,801 kWh x 0,235931 Eur/kWh 12,46 € | " +
            "Potencias contratadas: punta 6,928 kW; valle 6,928 kW | TOTAL 41,92 €\n" +
            "Output: {\"billingPeriodStart\":\"2023-12-31\",\"billingPeriodEnd\":\"2024-01-31\"," +
            "\"totalAmount\":41.92,\"consumptionKwh\":64.257," +
            "\"consumptionKwhP1\":null,\"consumptionKwhP2\":null,\"consumptionKwhP3\":null," +
            "\"pricePerKwh\":0.193909," +
            "\"pricePerKwhP1\":null,\"pricePerKwhP2\":null,\"pricePerKwhP3\":null,\"contractedPowerKw\":6.928}";

    private static final String GAS_INSTRUCTIONS =
            "Extract fields from the gas invoice delimited below.\n" +
            "Output ONLY valid JSON matching this schema (no prose, no markdown fences):\n" +
            "{\"billingPeriodStart\":\"YYYY-MM-DD\",\"billingPeriodEnd\":\"YYYY-MM-DD\"," +
            "\"totalAmount\":0.00,\"consumptionM3\":0.0,\"consumptionKwh\":0.0,\"pricePerKwh\":0.000}\n" +
            "ISO-8601 dates. Dot decimal separator. Null for any missing field.\n\n" +
            "Input: NATURGY Periodo 01/02/2024 al 28/02/2024 87 m³ 984 kWh 0,0712 €/kWh Total 89,34 €\n" +
            "Output: {\"billingPeriodStart\":\"2024-02-01\",\"billingPeriodEnd\":\"2024-02-28\"," +
            "\"totalAmount\":89.34,\"consumptionM3\":87.0,\"consumptionKwh\":984.0,\"pricePerKwh\":0.0712}";

    private static final String WATER_INSTRUCTIONS =
            "Extract fields from the water invoice delimited below.\n" +
            "Output ONLY valid JSON matching this schema (no prose, no markdown fences):\n" +
            "{\"billingPeriodStart\":\"YYYY-MM-DD\",\"billingPeriodEnd\":\"YYYY-MM-DD\"," +
            "\"totalAmount\":0.00,\"consumptionM3\":0.0,\"pricePerM3\":0.000,\"sewageCharge\":0.00}\n" +
            "ISO-8601 dates. Dot decimal separator. sewageCharge = saneamiento line item, null if absent.\n\n" +
            "Input: AGUAS MADRID Periodo 01/03/2024 al 31/05/2024 18 m³ 0,8234 €/m³ Saneamiento 12,40 € Total 39,21 €\n" +
            "Output: {\"billingPeriodStart\":\"2024-03-01\",\"billingPeriodEnd\":\"2024-05-31\"," +
            "\"totalAmount\":39.21,\"consumptionM3\":18.0,\"pricePerM3\":0.8234,\"sewageCharge\":12.40}";

    private static final String TELECOM_INSTRUCTIONS = buildTelecomInstructions();

    private static String buildTelecomInstructions() {
        return telecomSchema()
                + telecomLineDetection()
                + telecomLineClassification()
                + telecomBundleInference()
                + telecomAmounts()
                + telecomStreamingServices()
                + telecomFewShot();
    }

    private static final String JSON_REPAIR_INSTRUCTIONS =
            "The text below is malformed JSON. Return ONLY the corrected JSON — no prose, no markdown.";

    private static final String REPAIR_LABEL = "MALFORMED_JSON";

    private static final int MAX_REPAIR_CHARS = 8_000;

    // Registry: add a new SupplyDomain here without touching extract().
    private record ExtractionConfig(String instructions, Class<? extends InvoiceFields> fieldType) {}

    private static final Map<SupplyDomain, ExtractionConfig> CONFIGS = Map.of(
            SupplyDomain.ELECTRICITY, new ExtractionConfig(ELECTRICITY_INSTRUCTIONS, ElectricityFields.class),
            SupplyDomain.GAS,         new ExtractionConfig(GAS_INSTRUCTIONS,         GasFields.class),
            SupplyDomain.WATER,       new ExtractionConfig(WATER_INSTRUCTIONS,       WaterFields.class),
            SupplyDomain.TELECOM,     new ExtractionConfig(TELECOM_INSTRUCTIONS,     TelecomFields.class)
    );

    private final ChatModel              chatModel;
    private final ObjectMapper           objectMapper;
    private final ExtractionPromptBuilder promptBuilder;
    private final LlmResponseJsonSanitizer jsonSanitizer;
    private final InvoiceFieldsValidator  validator;

    public LlmInvoiceFieldExtractor(
            @Qualifier("smartChatModel") ChatModel chatModel,
            ObjectMapper objectMapper,
            ExtractionPromptBuilder promptBuilder,
            LlmResponseJsonSanitizer jsonSanitizer,
            InvoiceFieldsValidator validator) {
        this.chatModel     = chatModel;
        this.objectMapper  = objectMapper;
        this.promptBuilder = promptBuilder;
        this.jsonSanitizer = jsonSanitizer;
        this.validator     = validator;
    }

    @Override
    public InvoiceFields extract(String invoiceText, SupplyDomain type) {
        ExtractionConfig config = CONFIGS.get(type);
        if (config == null) throw new IllegalArgumentException("No extraction config for: " + type);

        MDC.put(TimedChatLanguageModel.MDC_OPERATION, "field-extraction");
        MDC.put(TimedChatLanguageModel.MDC_TYPE, type.name());
        try {
            String prompt  = promptBuilder.build(config.instructions(), invoiceText);
            long   startNs = System.nanoTime();
            log.info("Field extraction started [type={}]", config.fieldType().getSimpleName());

            // Provider failures arrive already classified from TimedChatLanguageModel: a 429 stays a
            // 429 all the way to the caller instead of collapsing into "service unavailable".
            String rawResponse = chatModel.chat(prompt);
            log.debug("LLM response [type={}]: {}", config.fieldType().getSimpleName(), rawResponse);

            InvoiceFields parsed;
            try {
                parsed = parse(rawResponse, config.fieldType());
            } catch (InvoiceFieldExtractionException e) {
                log.warn("JSON parse failed [type={}] — retrying with repair", config.fieldType().getSimpleName());
                parsed = repairAndParse(rawResponse, config.fieldType());
            }

            validator.validate(parsed);
            log.info("Field extraction succeeded [type={}, latencyMs={}]",
                    config.fieldType().getSimpleName(), elapsedMs(startNs));
            return parsed;
        } finally {
            MDC.remove(TimedChatLanguageModel.MDC_OPERATION);
            MDC.remove(TimedChatLanguageModel.MDC_TYPE);
        }
    }

    private InvoiceFields repairAndParse(String malformed, Class<? extends InvoiceFields> fieldType) {
        MDC.put(TimedChatLanguageModel.MDC_OPERATION, "json-repair");
        // The malformed text is the model's own prior output, derived from untrusted OCR — fence it
        // with a per-request nonce (the shared mechanism), never a fixed delimiter.
        PromptFence fence = PromptFence.random();
        String repairPrompt = JSON_REPAIR_INSTRUCTIONS + "\n\n"
                + fence.wrap(REPAIR_LABEL, malformed, MAX_REPAIR_CHARS) + "\nCorrected:";
        String repaired = chatModel.chat(repairPrompt);
        log.debug("Repair response [type={}]: {}", fieldType.getSimpleName(), repaired);
        try {
            return parse(repaired, fieldType);
        } catch (Exception ex) {
            log.error("Field extraction failed after repair [type={}]", fieldType.getSimpleName(), ex);
            throw new InvoiceFieldExtractionException(ex);
        }
    }

    private InvoiceFields parse(String response, Class<? extends InvoiceFields> fieldType) {
        try {
            return objectMapper.readValue(jsonSanitizer.sanitize(response), fieldType);
        } catch (Exception e) {
            throw new InvoiceFieldExtractionException(e);
        }
    }

    private static long elapsedMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000;
    }

    private static String telecomSchema() {
        return "Extract fields from the telecom invoice delimited below.\n"
                + "Output ONLY valid JSON matching this schema (no prose, no markdown fences):\n"
                + "{\"billingPeriodStart\":\"YYYY-MM-DD\",\"billingPeriodEnd\":\"YYYY-MM-DD\","
                + "\"totalAmount\":0.00,\"contractedSpeedMbps\":0,\"mobileDataGb\":0,"
                + "\"includedMobileLines\":0,\"mobileLineCount\":0,\"monthlyFee\":0.00,"
                + "\"lines\":[{\"lineType\":\"FIBRA|MOVIL|DISPOSITIVO|SERVICIO\",\"planName\":\"...\",\"baseAmount\":0.00,\"discount\":0.00}],"
                + "\"streamingServices\":[{\"platform\":\"...\",\"tier\":\"...\"}]}\n\n";
    }

    private static String telecomLineDetection() {
        return "=== LINE DETECTION ===\n"
                + "Priority 1: [TELÉFONO] token alone on a line → separator between entries. Strip it from output.\n"
                + "Priority 2 (no tokens): each distinct product/service name followed by its own billing period and amount is a separate line entry.\n\n";
    }

    private static String telecomLineClassification() {
        return "=== LINE CLASSIFICATION ===\n"
                + "FIBRA: internet/broadband (keywords: FIBRA, ADSL, INTERNET, or speed units Mb/MB/Mbps).\n"
                + "MOVIL: explicit mobile line entry (keywords when the entry stands alone: DUO, ADICIONAL, PRINCIPAL, LINEA, TARIFA, GO, O2, or preceded by [TELÉFONO]).\n"
                + "DISPOSITIVO: device installment — any CUOTA MENSUAL, FINANCIACIÓN, or named product (phone model, appliance).\n"
                + "SERVICIO: add-on billed as a separate line (streaming service, fixed fee not covered above).\n\n";
    }

    private static String telecomBundleInference() {
        return "=== CONVERGENT BUNDLE INFERENCE ===\n"
                + "A FIBRA line whose planName contains any mobile indicator (ILIMITADOS, ILIMITADO, SINFÍN, SINFIN, DATOS, DUO, GO, INFINITA, MOVIL, MÓVIL, or a GB quantity) implicitly includes 1 mobile line.\n"
                + "includedMobileLines = total count of FIBRA/bundle entries that contain a mobile indicator (normally 0 or 1).\n"
                + "mobileLineCount = (count of lines with lineType MOVIL) + includedMobileLines.\n\n";
    }

    private static String telecomAmounts() {
        return "=== AMOUNTS ===\n"
                + "lines[].baseAmount: when two amounts appear (e.g. '60,33€ 73,00€') take the FIRST (smaller, pre-IVA). Comma → dot.\n"
                + "lines[].discount: pre-IVA discount, negative (0.00 if none).\n"
                + "mobileDataGb: null when plan includes ILIMITADOS, ILIMITADO or SINFÍN; otherwise extract integer GB.\n"
                + "totalAmount: total invoice (all charges). monthlyFee: recurring telecom only (excludes DISPOSITIVO installments).\n\n";
    }

    private static String telecomStreamingServices() {
        return "=== STREAMING SERVICES ===\n"
                + "Scan ALL plan names. Normalize platform: NETFLIX→NETFLIX, DISNEY+→DISNEY_PLUS, MAX→MAX, APPLE TV+→APPLE_TV_PLUS, FILMIN→FILMIN.\n"
                + "tier (strict enum — pick the closest match, no other values allowed):\n"
                + "  CON ANUNCIOS — if text contains ANUNCIOS or ADS\n"
                + "  PREMIUM       — if text contains PREMIUM\n"
                + "  ESTÁNDAR      — if text contains ESTÁNDAR, ESTANDAR or STANDARD\n"
                + "  BÁSICO        — if text contains BÁSICO, BASICO or BASIC\n"
                + "  null          — if no tier keyword found\n"
                + "Empty array [] if no streaming platform found.\n\n";
    }

    private static String telecomFewShot() {
        return "ISO-8601 dates. Dot decimal separator. Integer fields: integer or null. Null for missing scalar fields.\n\n"
                + "Input: [TELÉFONO] - [TELÉFONO]\n"
                + "FIBRA 600Mb + LA SINFÍN GB ILIMITADOS + NETFLIX CON ANUNCIOS + DISNEY+ ANUNCIOS\n"
                + "01 Abr - 30 Abr 60,33€ 73,00€\n"
                + "Descuento 01 Abr - 30 Abr-33,22€-40,20€\n"
                + "[TELÉFONO]\n"
                + "LA DUO ADICIONAL 01 Abr - 30 Abr 7,44€ 9,00€\n"
                + "DESCUENTO 01 Abr - 30 Abr-2,48€-3,00€\n"
                + "[TELÉFONO]\n"
                + "LA DUO PRINCIPAL 01 Abr - 30 Abr 5,79€ 7,00€\n"
                + "DESCUENTO 01 Abr - 30 Abr-4,96€-6,00€\n"
                + "CUOTA MENSUAL (22/24) - XIAOMI SMART AIR FRYER 6,5L 01 Abr - 30 Abr 2,50€ 3,03€\n"
                + "Total 35,40€\n"
                + "Output: {\"billingPeriodStart\":\"2024-04-01\",\"billingPeriodEnd\":\"2024-04-30\","
                + "\"totalAmount\":37.93,\"contractedSpeedMbps\":600,\"mobileDataGb\":null,"
                + "\"includedMobileLines\":1,\"mobileLineCount\":3,\"monthlyFee\":34.90,"
                + "\"lines\":["
                + "{\"lineType\":\"FIBRA\",\"planName\":\"FIBRA 600Mb + LA SINFÍN GB ILIMITADOS + NETFLIX CON ANUNCIOS + DISNEY+ ANUNCIOS\",\"baseAmount\":60.33,\"discount\":-33.22},"
                + "{\"lineType\":\"MOVIL\",\"planName\":\"LA DUO ADICIONAL\",\"baseAmount\":7.44,\"discount\":-2.48},"
                + "{\"lineType\":\"MOVIL\",\"planName\":\"LA DUO PRINCIPAL\",\"baseAmount\":5.79,\"discount\":-4.96},"
                + "{\"lineType\":\"DISPOSITIVO\",\"planName\":\"CUOTA MENSUAL (22/24) - XIAOMI SMART AIR FRYER 6,5L\",\"baseAmount\":2.50,\"discount\":0.00}"
                + "],"
                + "\"streamingServices\":["
                + "{\"platform\":\"NETFLIX\",\"tier\":\"CON ANUNCIOS\"},"
                + "{\"platform\":\"DISNEY_PLUS\",\"tier\":\"CON ANUNCIOS\"}"
                + "]}";
    }
}