package dev.izquierdo.billmind.invoice.infrastructure.adapter.fieldextractor;

import dev.izquierdo.billmind._shared.infrastructure.llm.prompt.PromptFence;
import org.springframework.stereotype.Component;

/**
 * Assembles the final LLM prompt from an instruction block and raw OCR text.
 *
 * Applies the sandwich pattern (instructions → fenced data → instructions) consistently across all
 * invoice types. Sanitizing and delimiting the untrusted OCR belongs to {@link PromptFence}, the
 * shared mechanism; this class only owns the extraction-specific wording and length cap.
 */
@Component
public class ExtractionPromptBuilder {

    static final int MAX_OCR_CHARS = 8_000;

    static final String OCR_LABEL = "FACTURA_OCR";

    // Enumerating forbidden behaviours ("no reasoning, no calculations, no commentary") held on a
    // clean 2-page OCR and leaked ~470 tokens back on a messy 7-page one — ~9s, billed as output
    // and then discarded by LlmResponseJsonSanitizer. A list invites partial compliance; a
    // boundary does not.
    private static final String TRAILER =
            "Your entire reply is the JSON object and nothing else: its first character is '{'\n"
            + "and its last is '}'. Reason silently. Any text outside those braces — before or\n"
            + "after, including calculations, verification steps or commentary — is a failed\n"
            + "answer, however correct the JSON inside it may be.\n"
            + "Output:";

    public String build(String instructions, String rawOcrText) {
        // A fresh fence per build: the nonce must not be predictable from a previously scanned PDF.
        PromptFence fence = PromptFence.random();
        return instructions + "\n\n"
                + fence.wrap(OCR_LABEL, rawOcrText, MAX_OCR_CHARS) + "\n"
                + TRAILER;
    }
}