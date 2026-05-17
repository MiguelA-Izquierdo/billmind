package dev.izquierdo.billmind._shared.infrastructure.llm;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a clean JSON object from a raw LLM response.
 *
 * Handles common failure modes:
 *   - Markdown code fences: ```json\n{...}\n```
 *   - Leading prose or label prefixes ("Output:", "JSON:", etc.)
 *   - Trailing explanation text after the closing brace
 */
@Component
public class LlmResponseJsonSanitizer {

    private static final Pattern CODE_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");

    public String sanitize(String response) {
        Matcher fence = CODE_FENCE.matcher(response);
        String candidate = fence.find() ? fence.group(1).strip() : response.strip();
        int start = candidate.indexOf('{');
        int end   = candidate.lastIndexOf('}');
        if (start < 0 || end < 0 || start > end) return candidate;
        return candidate.substring(start, end + 1);
    }
}