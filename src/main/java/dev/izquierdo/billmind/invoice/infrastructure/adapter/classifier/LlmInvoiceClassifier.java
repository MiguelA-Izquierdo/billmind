package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import dev.izquierdo.billmind.invoice.domain.model.InvoiceClassification;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class LlmInvoiceClassifier {

    private static final Logger log = LoggerFactory.getLogger(LlmInvoiceClassifier.class);

    private static final int HEADER_CHARS          = 200;
    private static final int COMPANY_PREVIEW_CHARS = 250;
    private static final int COMPANY_MAX_ATTEMPTS  = 3;
    private static final String UNKNOWN_COMPANY    = "DESCONOCIDA";

    private static final String COMPANY_PROMPT =
            "From the following invoice fragment, identify the company that ISSUES it (the biller, not the customer).\n" +
            "The text may contain merged or fragmented words due to PDF font encoding issues.\n" +
            "Reply with ONLY the commercial name in UPPERCASE, without legal suffixes (S.A./S.L./S.A.U./etc.).\n" +
            "If you cannot identify it with confidence: DESCONOCIDA\n\n" +
            "Fragment: AUDAX ENERGÍA S.A. CUPS ES0031 factura electricidad período 01/01/2024\n" +
            "Issuer: AUDAX\n\n" +
            "Fragment: HOLALUZ-CLIDOM S.A. potencia contratada 4.6 kW energía consumida 312 kWh\n" +
            "Issuer: HOLALUZ\n\n" +
            "Fragment: REPSOL ELECTRICIDAD Y GAS S.A.U. importe total 87.43 EUR fecha vencimiento\n" +
            "Issuer: REPSOL\n\n" +
            "Fragment:\n%s\n\nIssuer:";

    private static final String FEW_SHOT_PROMPT =
            "Classify whether the text is a Spanish household utility invoice.\n" +
            "Types: ELECTRICITY=electricity, GAS=natural gas, WATER=water/sewage, TELECOM=phone/internet.\n" +
            "If it is not a utility invoice or you cannot determine it: OTHER.\n" +
            "Extract the issuer in uppercase. If you cannot identify it: DESCONOCIDA.\n" +
            "Reply with JSON only: {\"tipo\":\"...\",\"compania\":\"...\"}\n\n" +
            "Texto: IBERDROLA CUPS ES0031 405 kWh potencia contratada 3.3kW\n" +
            "JSON: {\"tipo\":\"ELECTRICITY\",\"compania\":\"IBERDROLA\"}\n\n" +
            "Texto: NATURGY 234 m³ consumo gas peaje transporte\n" +
            "JSON: {\"tipo\":\"GAS\",\"compania\":\"NATURGY\"}\n\n" +
            "Texto: Contrato de arrendamiento de local comercial firmado\n" +
            "JSON: {\"tipo\":\"OTHER\",\"compania\":\"DESCONOCIDA\"}\n\n" +
            "Texto: %s\n" +
            "JSON:";

    private final ChatModel chatModel;
    private final LlmResponseParser responseParser;

    public LlmInvoiceClassifier(@Qualifier("fastChatModel") ChatModel chatModel, LlmResponseParser responseParser) {
        this.chatModel      = chatModel;
        this.responseParser = responseParser;
    }

    public InvoiceClassification classify(String text) {
        String preview  = collapseFragmented(text.length() > HEADER_CHARS ? text.substring(0, HEADER_CHARS) : text);
        String prompt   = FEW_SHOT_PROMPT.formatted(preview);
        log.debug("Sending classification prompt ({} chars)", prompt.length());
        String response = chatModel.chat(prompt);
        log.debug("Respuesta LLM clasificación: {}", response);
        return responseParser.parse(response);
    }

    public String extractCompany(String text) {
        for (int attempt = 0; attempt < COMPANY_MAX_ATTEMPTS; attempt++) {
            int from   = attempt * COMPANY_PREVIEW_CHARS;
            int to     = Math.min(from + COMPANY_PREVIEW_CHARS, text.length());
            if (from >= text.length()) break;

            String preview = collapseFragmented(text.substring(from, to));
            String result  = chatModel.chat(COMPANY_PROMPT.formatted(preview)).strip();
            log.debug("extractCompany attempt {}/{} (chars {}-{}): {}", attempt + 1, COMPANY_MAX_ATTEMPTS, from, to, result);

            if (!UNKNOWN_COMPANY.equals(result)) return result;
        }
        log.debug("Company not identified after {} attempts", COMPANY_MAX_ATTEMPTS);
        return UNKNOWN_COMPANY;
    }

    /**
     * Joins consecutive single-character tokens when the text is genuinely fragmented
     * (>50% of tokens are exactly 1 char, as happens with PDF font-encoding splits like "I B E R D R O L A").
     * Uses ≤1 char (not ≤2) to avoid false merges of normal Spanish short words (de, el, en, la, al...).
     */
    private String collapseFragmented(String preview) {
        String[] tokens = preview.split(" ");
        int shortCount = 0;
        for (String t : tokens) if (t.length() <= 1) shortCount++;
        if (shortCount * 2 < tokens.length) return preview;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            sb.append(tokens[i]);
            if (i < tokens.length - 1) {
                boolean curShort  = tokens[i].length()     <= 1;
                boolean nextShort = tokens[i + 1].length() <= 1;
                if (!curShort || !nextShort) sb.append(' ');
            }
        }
        return sb.toString();
    }
}
