package dev.izquierdo.billmind._shared.infrastructure.llm.prompt;

/**
 * Renders a short untrusted value safe for inline interpolation into a prompt line — a company or
 * tariff name arriving from the external Kafka producer, or an argument the model itself supplied
 * and we echo back.
 *
 * <p>Complements {@link PromptFence} rather than replacing it. A fence stops content escaping its
 * block; it does not stop content forging structure *inside* the block. The market list marks rows
 * with line breaks and indentation, so a company name carrying a newline fabricates an extra tariff
 * row the model cannot tell from a real one. Collapsing whitespace and capping length closes that.
 */
public final class PromptText {

    /** Any whitespace run, including the line breaks that would forge an extra row. */
    private static final String WHITESPACE_RUN = "\\s+";

    private static final String CONTROL_CHARS = "\\p{Cntrl}";

    /** Rendered when a value is absent, matching the formatters' convention for missing data. */
    private static final String ABSENT = "—";

    private PromptText() {
    }

    /**
     * Collapses the value to a single line, strips control characters and caps it at
     * {@code maxChars}. Null, blank or whitespace-only yields an absent marker.
     */
    public static String inline(String value, int maxChars) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be positive, but is: " + maxChars);
        }
        if (value == null || value.isBlank()) {
            return ABSENT;
        }
        String flattened = value.replaceAll(CONTROL_CHARS, " ").replaceAll(WHITESPACE_RUN, " ").strip();
        if (flattened.isEmpty()) {
            return ABSENT;
        }
        return flattened.length() > maxChars ? flattened.substring(0, maxChars) : flattened;
    }
}