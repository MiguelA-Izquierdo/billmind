package dev.izquierdo.billmind._shared.infrastructure.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.izquierdo.billmind._shared.infrastructure.pii.PiiScrubber;

/**
 * Logback converter that scrubs PII from the rendered log message (arguments already
 * substituted) before it reaches any appender. Defence-in-depth safety net for accidental
 * leakage — the primary rule is still not to log invoice content in the first place.
 * Registered as the {@code %pii} conversion word in logback-spring.xml.
 * Fail-closed: never emits the raw message if scrubbing throws.
 *
 * <p>Uses {@link PiiScrubber#redactForLogs} rather than the full scrub: the postal-code pattern is
 * five bare digits, which on a log line is a latency or a counter far more often than an address.</p>
 */
public class PiiRedactingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        try {
            return PiiScrubber.redactForLogs(message);
        } catch (RuntimeException e) {
            return "[log-redaction-error]";
        }
    }
}