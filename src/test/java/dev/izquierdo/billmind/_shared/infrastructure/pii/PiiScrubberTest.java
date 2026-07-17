package dev.izquierdo.billmind._shared.infrastructure.pii;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiScrubberTest {

    // ── IBAN ─────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactCompactIban() {
        String result = PiiScrubber.redact("Cuenta: ES9121000418450200051332 para domiciliación");
        assertThat(result).contains("[IBAN]").doesNotContain("ES91");
    }

    @Test
    void shouldRedactFormattedIban() {
        String result = PiiScrubber.redact("Cuenta: ES91 2100 0418 4502 0005 1332");
        assertThat(result).contains("[IBAN]").doesNotContain("ES91");
    }

    @Test
    void shouldNotRedactNonSpanishIban() {
        String result = PiiScrubber.redact("IBAN extranjero: FR7630006000011234567890189");
        assertThat(result).doesNotContain("[IBAN]").contains("FR76");
    }

    // ── DNI ──────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactDni() {
        String result = PiiScrubber.redact("Titular DNI 12345678Z domicilio");
        assertThat(result).contains("[DNI]").doesNotContain("12345678Z");
    }

    @Test
    void shouldNotRedactDniWithLowercaseLetter() {
        String result = PiiScrubber.redact("ref 12345678z código");
        assertThat(result).doesNotContain("[DNI]");
    }

    @Test
    void shouldNotRedactSevenDigitSequence() {
        String result = PiiScrubber.redact("referencia 1234567Z valor");
        assertThat(result).doesNotContain("[DNI]");
    }

    // ── NIE ──────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactNieStartingWithX() {
        String result = PiiScrubber.redact("NIE: X1234567Z");
        assertThat(result).contains("[NIE]").doesNotContain("X1234567Z");
    }

    @Test
    void shouldRedactNieStartingWithY() {
        String result = PiiScrubber.redact("NIE Y9876543A del titular");
        assertThat(result).contains("[NIE]").doesNotContain("Y9876543A");
    }

    @Test
    void shouldRedactNieStartingWithZ() {
        String result = PiiScrubber.redact("Extranjero Z0000001B");
        assertThat(result).contains("[NIE]");
    }

    // ── CIF ──────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactCifWithLetterControl() {
        String result = PiiScrubber.redact("Emisor CIF A1234567H facturación");
        assertThat(result).contains("[CIF]").doesNotContain("A1234567H");
    }

    @Test
    void shouldRedactCifWithDigitControl() {
        String result = PiiScrubber.redact("CIF: B12345678");
        assertThat(result).contains("[CIF]").doesNotContain("B12345678");
    }

    // ── TELÉFONO ─────────────────────────────────────────────────────────────

    @Test
    void shouldRedactMobilePhone() {
        String result = PiiScrubber.redact("Contacto: 600123456 horario comercial");
        assertThat(result).contains("[TELÉFONO]").doesNotContain("600123456");
    }

    @Test
    void shouldRedactLandlinePhone() {
        String result = PiiScrubber.redact("Teléfono 912345678 atención al cliente");
        assertThat(result).contains("[TELÉFONO]").doesNotContain("912345678");
    }

    @Test
    void shouldRedactFormattedPhone() {
        String result = PiiScrubber.redact("Tel: 600-123-456");
        assertThat(result).contains("[TELÉFONO]");
    }

    @Test
    void shouldNotRedactNumberStartingWithFive() {
        String result = PiiScrubber.redact("referencia 512345678 interna");
        assertThat(result).doesNotContain("[TELÉFONO]");
    }

    // ── EMAIL ────────────────────────────────────────────────────────────────

    @Test
    void shouldRedactEmail() {
        String result = PiiScrubber.redact("Contacto: cliente@ejemplo.es para consultas");
        assertThat(result).contains("[EMAIL]").doesNotContain("cliente@ejemplo.es");
    }

    @Test
    void shouldRedactEmailWithSubdomain() {
        String result = PiiScrubber.redact("usuario@correo.empresa.com");
        assertThat(result).contains("[EMAIL]");
    }

    // ── CÓDIGO POSTAL ────────────────────────────────────────────────────────

    @Test
    void shouldRedactValidPostalCode() {
        String result = PiiScrubber.redact("Dirección: Calle Mayor 1, 28001 Madrid");
        assertThat(result).contains("[CP]").doesNotContain("28001");
    }

    @Test
    void shouldRedactBarcelonaPostalCode() {
        String result = PiiScrubber.redact("Domicilio en 08001 Barcelona");
        assertThat(result).contains("[CP]").doesNotContain("08001");
    }

    @Test
    void shouldNotRedactInvalidProvince() {
        String result = PiiScrubber.redact("código 53001 no existe");
        assertThat(result).doesNotContain("[CP]").contains("53001");
    }

    // ── MÚLTIPLES PII / CASOS LÍMITE ─────────────────────────────────────────

    @Test
    void shouldRedactMultiplePiiTypesInOneText() {
        String text = "Titular: 12345678Z, email: a@b.com, cuenta ES9121000418450200051332, CP 28001";
        String result = PiiScrubber.redact(text);

        assertThat(result)
            .contains("[DNI]", "[EMAIL]", "[IBAN]", "[CP]")
            .doesNotContain("12345678Z", "a@b.com", "ES91", "28001");
    }

    @Test
    void shouldNotAlterTextWithNoPii() {
        String text = "Potencia contratada: 3,3 kW. Consumo: 245 kWh. Precio: 0,18 €/kWh.";
        assertThat(PiiScrubber.redact(text)).isEqualTo(text);
    }

    @Test
    void shouldReturnNullWhenNull() {
        assertThat(PiiScrubber.redact(null)).isNull();
    }

    @Test
    void shouldReturnEmptyWhenEmpty() {
        assertThat(PiiScrubber.redact("")).isEmpty();
    }
}