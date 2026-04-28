package com.demo.billmind.invoice.infrastructure.adapter.classifier;

import com.demo.billmind.invoice.domain.model.InvoiceClassification;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmInvoiceClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmInvoiceClassifier.class);

    private static final int HEADER_CHARS          = 200;
    private static final int COMPANY_PREVIEW_CHARS = 250;

    private static final String COMPANY_PROMPT =
            "En el siguiente fragmento de factura identifica la empresa que la EMITE (quien cobra, no el cliente).\n" +
            "El texto puede tener palabras pegadas o fragmentadas por el encoding del PDF.\n" +
            "Responde SOLO el nombre comercial en MAYÚSCULAS, sin forma jurídica (S.A./S.L./S.A.U./etc.).\n" +
            "Si no puedes identificarla con seguridad: DESCONOCIDA\n\n" +
            "Fragmento:\n%s\n\nEmisor:";

    private static final String FEW_SHOT_PROMPT =
            "Clasifica si el texto es una factura de suministro del hogar.\n" +
            "Tipos: LUZ=electricidad, GAS=gas natural, AGUA=agua/saneamiento, TELCO=telefonía/internet.\n" +
            "Si no es factura de suministro o no puedes determinarlo: OTRO.\n" +
            "Extrae el emisor en mayúsculas. Si no lo identificas: DESCONOCIDA.\n" +
            "Responde solo JSON: {\"tipo\":\"...\",\"compania\":\"...\"}\n\n" +
            "Texto: IBERDROLA CUPS ES0031 405 kWh potencia contratada 3.3kW\n" +
            "JSON: {\"tipo\":\"LUZ\",\"compania\":\"IBERDROLA\"}\n\n" +
            "Texto: NATURGY 234 m³ consumo gas peaje transporte\n" +
            "JSON: {\"tipo\":\"GAS\",\"compania\":\"NATURGY\"}\n\n" +
            "Texto: Contrato de arrendamiento de local comercial firmado\n" +
            "JSON: {\"tipo\":\"OTRO\",\"compania\":\"DESCONOCIDA\"}\n\n" +
            "Texto: %s\n" +
            "JSON:";

    private final ChatLanguageModel chatModel;
    private final LlmResponseParser responseParser;

    public LlmInvoiceClassifier(ChatLanguageModel chatModel, LlmResponseParser responseParser) {
        this.chatModel      = chatModel;
        this.responseParser = responseParser;
    }

    public InvoiceClassification classify(String text) {
        String preview  = collapseFragmented(text.length() > HEADER_CHARS ? text.substring(0, HEADER_CHARS) : text);
        String prompt   = FEW_SHOT_PROMPT.formatted(preview);
        log.info(">>> [PROMPT classifyByLlm] >>>\n{}\n<<<", prompt);
        String response = chatModel.generate(prompt);
        log.debug("Respuesta LLM clasificación: {}", response);
        return responseParser.parse(response);
    }

    public String extractCompany(String text) {
        String preview = collapseFragmented(text.length() > COMPANY_PREVIEW_CHARS ? text.substring(0, COMPANY_PREVIEW_CHARS) : text);
        String prompt  = COMPANY_PROMPT.formatted(preview);
        log.info(">>> [PROMPT extractCompany] >>>\n{}\n<<<", prompt);
        return chatModel.generate(prompt).strip();
    }

    /**
     * Une tokens cortos consecutivos (≤2 chars) eliminando el espacio entre ellos.
     * Corrige PDFs con texto fragmentado carácter a carácter en el header,
     * donde "En de sa E ne rg ía , S .A" debe leerse como "EndesaEnergía,S.A".
     * Se aplica sobre el preview (150-200 chars) donde la fragmentación es detectable.
     */
    private String collapseFragmented(String preview) {
        String[] tokens = preview.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            sb.append(tokens[i]);
            if (i < tokens.length - 1) {
                boolean curShort  = tokens[i].length()     <= 2;
                boolean nextShort = tokens[i + 1].length() <= 2;
                if (!curShort || !nextShort) sb.append(' ');
            }
        }
        return sb.toString();
    }
}
