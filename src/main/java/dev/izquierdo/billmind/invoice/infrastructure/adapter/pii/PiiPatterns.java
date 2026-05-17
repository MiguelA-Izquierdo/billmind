package dev.izquierdo.billmind.invoice.infrastructure.adapter.pii;

import java.util.regex.Pattern;

final class PiiPatterns {

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

    private PiiPatterns() {}

    static String redact(String text) {
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