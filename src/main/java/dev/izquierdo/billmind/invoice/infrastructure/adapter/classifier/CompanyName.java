package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

/**
 * Guards the one value the classifier takes verbatim from the model: the issuing company.
 *
 * <p>It is persisted into {@code invoices.provider}, a {@code varchar(255)}, so an answer that is
 * an explanation rather than a name reaches the database as an oversized row. Both producers — the
 * {@code extractCompany} loop and the {@code compania} field of the classification JSON — route
 * through here so the rule lives in one place.
 */
final class CompanyName {

    static final String UNKNOWN = "DESCONOCIDA";

    /** Real company names are short and fit on one line; anything else is the model explaining itself. */
    static final int MAX_CHARS = 60;

    private CompanyName() {}

    /**
     * Blank stays blank — what "no company" means is the caller's decision. A non-blank value that
     * cannot be a commercial name becomes {@link #UNKNOWN}, the answer the callers already handle.
     */
    static String sanitize(String raw) {
        if (raw == null) return "";
        String value = raw.strip();
        if (value.isEmpty()) return "";
        return isPlausible(value) ? value : UNKNOWN;
    }

    private static boolean isPlausible(String value) {
        return value.length() <= MAX_CHARS
                && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0;
    }
}