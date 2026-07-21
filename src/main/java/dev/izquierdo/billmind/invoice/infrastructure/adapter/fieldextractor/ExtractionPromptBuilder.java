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

    private static final String TRAILER = "Output:";

    public String build(String instructions, String rawOcrText) {
        // A fresh fence per build: the nonce must not be predictable from a previously scanned PDF.
        PromptFence fence = PromptFence.random();
        return instructions + "\n\n"
                + fence.wrap(OCR_LABEL, rawOcrText, MAX_OCR_CHARS) + "\n"
                + TRAILER;
    }
}