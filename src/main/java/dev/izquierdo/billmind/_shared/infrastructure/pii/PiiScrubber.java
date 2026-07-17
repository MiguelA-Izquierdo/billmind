package dev.izquierdo.billmind._shared.infrastructure.pii;

import java.util.regex.Pattern;

/**
 * Pure, dependency-free regex PII scrubber shared across bounded contexts.
 * Single source of truth for the Spanish PII patterns: reused by the invoice
 * ingestion redactor and by the logging pipeline (see PiiRedactingMessageConverter).
 * Deterministic and side-effect free — safe on any hot path, including log appenders.
 */
public final class PiiScrubber {

    // ES + 2 check digits + 20 account digits (groups of 4, optional separators)
    private static final Pattern IBAN = Pattern.compile(
        "\\bES\\d{2}(?:[\\s-]?\\d{4}){5}\\b"
    );
    private static final Pattern DNI = Pattern.compile(
        "\\b\\d{8}[A-Z]\\b"
    );
    private static final Pattern NIE = Pattern.compile(
        "\\b[XYZ]\\d{7}[A-Z]\\b"
    );
    // Valid first letters for Spanish CIF
    private static final Pattern CIF = Pattern.compile(
        "\\b[A-HJNPQRSUVW]\\d{7}[A-J0-9]\\b"
    );
    private static final Pattern PHONE = Pattern.compile(
        "\\b(?:\\+34[\\s.]?)?[6789]\\d{2}[\\s.-]?\\d{3}[\\s.-]?\\d{3}\\b"
    );
    private static final Pattern EMAIL = Pattern.compile(
        "\\b[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}\\b"
    );
    // Spanish provinces: 01–52 as first two digits
    private static final Pattern POSTAL_CODE = Pattern.compile(
        "\\b(?:0[1-9]|[1-4]\\d|5[0-2])\\d{3}\\b"
    );

    private PiiScrubber() {}

    /** Redacts known PII tokens. Null-safe: returns the input unchanged when null or blank. */
    public static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        text = IBAN.matcher(text).replaceAll("[IBAN]");
        text = DNI.matcher(text).replaceAll("[DNI]");
        text = NIE.matcher(text).replaceAll("[NIE]");
        text = CIF.matcher(text).replaceAll("[CIF]");
        text = PHONE.matcher(text).replaceAll("[TELÉFONO]");
        text = EMAIL.matcher(text).replaceAll("[EMAIL]");
        text = POSTAL_CODE.matcher(text).replaceAll("[CP]");
        return text;
    }
}