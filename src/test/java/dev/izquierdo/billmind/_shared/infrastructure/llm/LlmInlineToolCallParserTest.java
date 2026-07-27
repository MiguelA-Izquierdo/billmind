package dev.izquierdo.billmind._shared.infrastructure.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmInlineToolCallParserTest {

    // ── Detection ─────────────────────────────────────────────────────────────

    @Test
    void shouldDetectMarkupWhenFunctionTagPresent() {
        assertThat(LlmInlineToolCallParser.containsToolMarkup(
                "text <function=foo></function>")).isTrue();
    }

    @Test
    void shouldNotDetectMarkupInPlainText() {
        assertThat(LlmInlineToolCallParser.containsToolMarkup("plain answer")).isFalse();
        assertThat(LlmInlineToolCallParser.containsToolMarkup(null)).isFalse();
    }

    // ── Parsing (the two shapes observed from llama-3.3-70b-versatile) ─────────

    @Test
    void shouldParseArgumentlessTagWithClosingBracket() {
        List<ToolExecutionRequest> calls = LlmInlineToolCallParser.parse(
                "Para comparar. <function=get_invoice_comparison></function> Luego decides.");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("get_invoice_comparison");
        assertThat(calls.get(0).arguments()).isEqualTo("{}");
        assertThat(calls.get(0).id()).isNotBlank();
    }

    @Test
    void shouldParseTagWithStrayEqualsAndJsonArguments() {
        List<ToolExecutionRequest> calls = LlmInlineToolCallParser.parse(
                "<function=search_market_rates={\"company\":\"Naturgy\"}</function>");

        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("search_market_rates");
        assertThat(calls.get(0).arguments()).isEqualTo("{\"company\":\"Naturgy\"}");
    }

    @Test
    void shouldParseMultipleInlineCallsInOrder() {
        List<ToolExecutionRequest> calls = LlmInlineToolCallParser.parse(
                "<function=get_invoice_comparison></function> y "
                        + "<function=search_market_rates={\"company\":\"Endesa\"}</function>");

        assertThat(calls).extracting(ToolExecutionRequest::name)
                .containsExactly("get_invoice_comparison", "search_market_rates");
    }

    @Test
    void shouldGenerateDistinctIdsPerCall() {
        List<ToolExecutionRequest> calls = LlmInlineToolCallParser.parse(
                "<function=a></function><function=b></function>");

        assertThat(calls.get(0).id()).isNotEqualTo(calls.get(1).id());
    }

    @Test
    void shouldReturnEmptyForNullOrPlainText() {
        assertThat(LlmInlineToolCallParser.parse(null)).isEmpty();
        assertThat(LlmInlineToolCallParser.parse("just a normal answer")).isEmpty();
    }

    // ── Stripping ─────────────────────────────────────────────────────────────

    @Test
    void shouldStripCompleteTagsAndCollapseBlankLines() {
        String leaked = "Para cambiar de tarifa, compara opciones. "
                + "<function=get_invoice_comparison></function>\n\n\n\nLuego decides.";

        String cleaned = LlmInlineToolCallParser.stripToolMarkup(leaked);

        assertThat(cleaned).doesNotContain("<function");
        assertThat(cleaned).doesNotContain("\n\n\n");
        assertThat(cleaned).isEqualTo("Para cambiar de tarifa, compara opciones. \n\nLuego decides.");
    }

    @Test
    void shouldStripDanglingUnclosedFragment() {
        String cleaned = LlmInlineToolCallParser.stripToolMarkup(
                "Respuesta útil <function=search_market_rates={\"company\":\"X\"}");

        assertThat(cleaned).doesNotContain("<function");
        assertThat(cleaned).isEqualTo("Respuesta útil");
    }

    @Test
    void shouldReturnEmptyStringWhenTextIsOnlyMarkup() {
        assertThat(LlmInlineToolCallParser.stripToolMarkup(
                "<function=get_invoice_comparison></function>")).isEmpty();
    }

    @Test
    void shouldPreservePlainTextUnchanged() {
        assertThat(LlmInlineToolCallParser.stripToolMarkup("Un texto normal sin markup."))
                .isEqualTo("Un texto normal sin markup.");
    }

    @Test
    void shouldReturnNullForNull() {
        assertThat(LlmInlineToolCallParser.stripToolMarkup(null)).isNull();
    }
}