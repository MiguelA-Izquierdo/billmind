package dev.izquierdo.billmind._shared.infrastructure.llm;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recovers and scrubs Llama-style inline tool calls that leak into an assistant message's text.
 *
 * <p>Groq's {@code llama-3.3-70b-versatile} intermittently emits a tool call as plain text using
 * Llama's native function-tag syntax — {@code <function=name>{args}</function>} — instead of
 * populating the API's structured {@code tool_calls} field. When that happens the provider returns
 * a text-only message, the caller sees no {@code toolExecutionRequests}, and the raw markup would
 * otherwise reach the end user. Observed variants are inconsistent, e.g.
 * {@code <function=get_invoice_comparison></function>} and
 * {@code <function=search_market_rates={"company":"Naturgy"}</function>} (a stray {@code =} in
 * place of the opening {@code >}), so the patterns are deliberately tolerant.
 *
 * <p>Pure and stateless: {@link #parse(String)} reconstructs the intended tool calls (each with a
 * fresh id so a synthesized {@code AiMessage} correlates with its {@code ToolExecutionResultMessage});
 * {@link #stripToolMarkup(String)} removes any residual markup from a final answer.
 */
public final class LlmInlineToolCallParser {

    /** Well-formed inline call: name in group 1, optional JSON args in group 2. */
    private static final Pattern FUNCTION_TAG = Pattern.compile(
            "<function=([a-zA-Z_]\\w*)\\s*[=>]?\\s*(\\{.*?\\})?\\s*</function>",
            Pattern.DOTALL);

    /** Any complete tag, for stripping. */
    private static final Pattern FUNCTION_TAG_LOOSE = Pattern.compile(
            "<function\\b.*?</function>", Pattern.DOTALL);

    /** Dangling fragments left when the model never closed the tag (or a stray close). */
    private static final Pattern FUNCTION_FRAGMENT = Pattern.compile(
            "</?function\\b[^>]*>?");

    /** Three or more consecutive newlines collapse to a paragraph break. */
    private static final Pattern EXCESS_BLANK_LINES = Pattern.compile("\\n{3,}");

    private LlmInlineToolCallParser() {
    }

    /** True when {@code text} carries at least one inline function tag. */
    public static boolean containsToolMarkup(String text) {
        return text != null && text.contains("<function");
    }

    /**
     * Parses inline function tags into tool execution requests. Returns an empty list when the text
     * is null or has no well-formed tag; malformed args are passed through verbatim for the tool's
     * own tolerant argument reading to handle.
     */
    public static List<ToolExecutionRequest> parse(String text) {
        List<ToolExecutionRequest> requests = new ArrayList<>();
        if (text == null) return requests;
        Matcher m = FUNCTION_TAG.matcher(text);
        while (m.find()) {
            String args = m.group(2) != null ? m.group(2).strip() : "{}";
            requests.add(ToolExecutionRequest.builder()
                    .id(UUID.randomUUID().toString())
                    .name(m.group(1))
                    .arguments(args)
                    .build());
        }
        return requests;
    }

    /** Removes any inline function markup (complete or dangling) and tidies the whitespace it leaves. */
    public static String stripToolMarkup(String text) {
        if (text == null) return null;
        String cleaned = FUNCTION_TAG_LOOSE.matcher(text).replaceAll("");
        cleaned = FUNCTION_FRAGMENT.matcher(cleaned).replaceAll("");
        cleaned = EXCESS_BLANK_LINES.matcher(cleaned).replaceAll("\n\n");
        return cleaned.strip();
    }
}