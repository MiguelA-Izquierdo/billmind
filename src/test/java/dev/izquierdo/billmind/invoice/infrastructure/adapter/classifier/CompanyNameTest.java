package dev.izquierdo.billmind.invoice.infrastructure.adapter.classifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyNameTest {

    @Test
    void shouldKeepACommercialName() {
        assertThat(CompanyName.sanitize("IBERDROLA")).isEqualTo("IBERDROLA");
    }

    @Test
    void shouldStripSurroundingWhitespace() {
        assertThat(CompanyName.sanitize("  ENDESA \n")).isEqualTo("ENDESA");
    }

    @Test
    void shouldKeepBlankAsBlankSoTheCallerDecides() {
        assertThat(CompanyName.sanitize("")).isEmpty();
        assertThat(CompanyName.sanitize("   ")).isEmpty();
        assertThat(CompanyName.sanitize(null)).isEmpty();
    }

    /** The bug this class exists for: the answer reached invoices.provider, a varchar(255). */
    @Test
    void shouldRejectAnAnswerLongerThanACompanyName() {
        String explanation = "The company that issues this invoice is IBERDROLA, ".repeat(10);

        assertThat(CompanyName.sanitize(explanation)).isEqualTo(CompanyName.UNKNOWN);
    }

    @Test
    void shouldRejectAMultiLineAnswer() {
        assertThat(CompanyName.sanitize("IBERDROLA\nIssuer: IBERDROLA")).isEqualTo(CompanyName.UNKNOWN);
    }

    @Test
    void shouldAcceptANameExactlyAtTheLimit() {
        String atLimit = "A".repeat(CompanyName.MAX_CHARS);

        assertThat(CompanyName.sanitize(atLimit)).isEqualTo(atLimit);
    }

    @Test
    void shouldRejectANameOneCharacterOverTheLimit() {
        assertThat(CompanyName.sanitize("A".repeat(CompanyName.MAX_CHARS + 1)))
                .isEqualTo(CompanyName.UNKNOWN);
    }
}