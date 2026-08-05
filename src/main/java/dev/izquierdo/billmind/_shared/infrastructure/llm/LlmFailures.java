package dev.izquierdo.billmind._shared.infrastructure.llm;

import dev.izquierdo.billmind._shared.domain.exceptions.LlmRateLimitedException;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmServiceUnavailableException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.exception.UnresolvedModelServerException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever a provider throws into one of our two user-facing failures. Applied in a single
 * place — {@link TimedChatLanguageModel#chat} — so every LLM call in the app is classified the same
 * way and no adapter has to know a provider's exception vocabulary.
 *
 * <p>Anything that is not a throttle or an outage passes through untouched, which keeps
 * {@code InvalidRequestException} available to the agentic loop: a malformed tool call is our bug to
 * recover from, not an incident to show the user.
 */
public final class LlmFailures {

    /** Providers differ on wording but all of them say some form of "you are going too fast". */
    private static final List<String> THROTTLE_HINTS = List.of(
            "rate limit", "rate_limit", "ratelimit", "too many requests", "429",
            "quota", "resource_exhausted", "overloaded");

    /** Groq/OpenAI put the wait in the body: "Please try again in 2m30.5s". */
    private static final Pattern TRY_AGAIN_IN = Pattern.compile(
            "try again in\\s+(?:(\\d+)h)?(?:(\\d+)m(?!s))?(?:([\\d.]+)s)?", Pattern.CASE_INSENSITIVE);

    /** Anthropic and others echo the header value instead: "retry-after: 30". */
    private static final Pattern RETRY_AFTER = Pattern.compile(
            "retry[-_ ]?after[\"']?\\s*[:=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private LlmFailures() {
    }

    /**
     * The exception to propagate for a failed chat call. Returns the original when the failure is
     * neither a throttle nor an outage, so callers that inspect provider types still work.
     */
    public static RuntimeException translate(Throwable failure) {
        List<Throwable> chain = chain(failure);
        if (isRateLimited(chain)) {
            return new LlmRateLimitedException(failure, retryAfterIn(chain));
        }
        if (isUnavailable(chain)) {
            return new LlmServiceUnavailableException(failure);
        }
        return failure instanceof RuntimeException runtime
                ? runtime
                : new LlmServiceUnavailableException(failure);
    }

    private static boolean isRateLimited(List<Throwable> chain) {
        return chain.stream().anyMatch(t ->
                t instanceof RateLimitException
                        || (t instanceof HttpException http && http.statusCode() == 429)
                        || mentionsThrottle(t));
    }

    private static boolean isUnavailable(List<Throwable> chain) {
        return chain.stream().anyMatch(t ->
                t instanceof InternalServerException
                        || t instanceof TimeoutException
                        || t instanceof UnresolvedModelServerException
                        || t instanceof AuthenticationException
                        || t instanceof ModelNotFoundException
                        || t instanceof java.io.IOException
                        || t instanceof java.util.concurrent.TimeoutException
                        || (t instanceof HttpException http && http.statusCode() >= 500));
    }

    /**
     * Only trusted on an {@link HttpException}: the words alone are too common to classify by, and a
     * model happily writes "rate limit" into an answer about electricity tariffs.
     */
    private static boolean mentionsThrottle(Throwable t) {
        if (!(t instanceof HttpException) || t.getMessage() == null) return false;
        String message = t.getMessage().toLowerCase(Locale.ROOT);
        return THROTTLE_HINTS.stream().anyMatch(message::contains);
    }

    /** The wait the provider asked for, or {@link Duration#ZERO} when it named none. */
    private static Duration retryAfterIn(List<Throwable> chain) {
        for (Throwable t : chain) {
            if (t.getMessage() == null) continue;
            Duration parsed = parseWait(t.getMessage());
            if (!parsed.isZero()) return parsed;
        }
        return Duration.ZERO;
    }

    private static Duration parseWait(String message) {
        Matcher tryAgain = TRY_AGAIN_IN.matcher(message);
        if (tryAgain.find()) {
            long seconds = 3600 * parseLong(tryAgain.group(1))
                    + 60 * parseLong(tryAgain.group(2))
                    + (long) Math.ceil(parseDouble(tryAgain.group(3)));
            if (seconds > 0) return Duration.ofSeconds(seconds);
        }
        Matcher retryAfter = RETRY_AFTER.matcher(message);
        return retryAfter.find() ? Duration.ofSeconds(parseLong(retryAfter.group(1))) : Duration.ZERO;
    }

    private static long parseLong(String value) {
        return value == null ? 0L : Long.parseLong(value);
    }

    private static double parseDouble(String value) {
        return value == null ? 0d : Double.parseDouble(value);
    }

    /** The exception and every cause below it; providers nest the real failure two or three deep. */
    private static List<Throwable> chain(Throwable failure) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = failure; current != null && !chain.contains(current); current = current.getCause()) {
            chain.add(current);
        }
        return chain;
    }
}