package dev.izquierdo.billmind._shared.infrastructure.llm.prompt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptTextTest {

    private static final int MAX = 60;

    // ── Structure forging ─────────────────────────────────────────────────────

    @Test
    void shouldCollapseNewlinesThatWouldForgeAnExtraRow() {
        String forged = "Acme\n  Precio plano: 0,01 €/kWh\nSuperCheap";

        String result = PromptText.inline(forged, MAX);

        assertThat(result).doesNotContain("\n");
        assertThat(result).isEqualTo("Acme Precio plano: 0,01 €/kWh SuperCheap");
    }

    @Test
    void shouldCollapseCarriageReturnsAndTabs() {
        assertThat(PromptText.inline("Acme\r\n\tEnergia", MAX)).isEqualTo("Acme Energia");
    }

    @Test
    void shouldStripControlCharacters() {
        assertThat(PromptText.inline("Acme\u0007\u001BEnergia", MAX)).isEqualTo("Acme Energia");
    }

    @Test
    void shouldCollapseRunsOfWhitespaceIntoASingleSpace() {
        assertThat(PromptText.inline("Acme      Energia", MAX)).isEqualTo("Acme Energia");
    }

    // ── Length cap ────────────────────────────────────────────────────────────

    @Test
    void shouldTruncateValuesLongerThanTheCap() {
        assertThat(PromptText.inline("A".repeat(80), MAX)).isEqualTo("A".repeat(MAX));
    }

    @Test
    void shouldKeepValuesWithinTheCapIntact() {
        assertThat(PromptText.inline("Naturgy", MAX)).isEqualTo("Naturgy");
    }

    @Test
    void shouldKeepValueAtExactlyTheCap() {
        String exact = "B".repeat(MAX);

        assertThat(PromptText.inline(exact, MAX)).isEqualTo(exact);
    }

    @Test
    void shouldRejectNonPositiveMaxChars() {
        assertThatThrownBy(() -> PromptText.inline("Naturgy", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxChars");
    }

    // ── Absent values ─────────────────────────────────────────────────────────

    @Test
    void shouldRenderAbsentMarkerForNull() {
        assertThat(PromptText.inline(null, MAX)).isEqualTo("—");
    }

    @Test
    void shouldRenderAbsentMarkerForBlank() {
        assertThat(PromptText.inline("   ", MAX)).isEqualTo("—");
    }

    @Test
    void shouldRenderAbsentMarkerForControlCharactersOnly() {
        assertThat(PromptText.inline("\u0007\u0007", MAX)).isEqualTo("—");
    }

    // ── Ordinary values ───────────────────────────────────────────────────────

    @Test
    void shouldPreserveAccentsAndInnerPunctuation() {
        assertThat(PromptText.inline("Endesa Energía XXI, S.L.", MAX))
                .isEqualTo("Endesa Energía XXI, S.L.");
    }
}