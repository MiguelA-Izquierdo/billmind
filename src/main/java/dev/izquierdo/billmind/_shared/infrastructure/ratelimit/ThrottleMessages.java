package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import java.time.Duration;

/**
 * The Spanish copy shown when a caller is asked to wait — whether our own bucket denied them or the
 * model provider did. Both are the same thing to the person reading it, so both read the same way.
 *
 * <p>The browser UI composes its own richer copy from the {@code Retry-After} header; this text is
 * what a plain API client (or a UI that ignores the header) gets to see.
 */
public final class ThrottleMessages {

    private ThrottleMessages() {
    }

    /** Our own limiter denied the request: the caller has spent their allowance. */
    public static String throttled(Duration retryAfter) {
        return "Has agotado tus consultas por ahora. " + retryPhrase(retryAfter);
    }

    /** The model provider throttled us; {@code base} is the exception's own message. */
    public static String llmThrottled(String base, Duration retryAfter) {
        return base + " " + retryPhrase(retryAfter);
    }

    private static String retryPhrase(Duration retryAfter) {
        return isUnknown(retryAfter)
                ? "Inténtalo de nuevo más tarde."
                : "Vuelve a intentarlo en " + humanize(retryAfter) + ".";
    }

    /**
     * Rounded up and coarse on purpose: "en 12 minutos" is honest and easy to act on, while
     * "en 11 minutos y 38 segundos" invites the user to sit and watch a clock.
     */
    public static String humanize(Duration retryAfter) {
        long seconds = (long) Math.ceil(retryAfter.toMillis() / 1000.0);
        if (seconds <= 60)   return "un minuto";
        long minutes = (long) Math.ceil(seconds / 60.0);
        if (minutes < 60)    return minutes + " minutos";
        long hours = (long) Math.ceil(minutes / 60.0);
        return hours == 1 ? "una hora" : hours + " horas";
    }

    private static boolean isUnknown(Duration retryAfter) {
        return retryAfter == null || retryAfter.isZero() || retryAfter.isNegative();
    }
}