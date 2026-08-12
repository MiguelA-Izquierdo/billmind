package dev.izquierdo.billmind._shared.infrastructure.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactingMessageConverterTest {

    private final PiiRedactingMessageConverter converter = new PiiRedactingMessageConverter();

    @Test
    void shouldRedactPiiFromPlainMessage() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Titular DNI 12345678Z domicilio");

        assertThat(converter.convert(event)).contains("[DNI]").doesNotContain("12345678Z");
    }

    @Test
    void shouldRedactPiiFromSubstitutedArguments() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("cuenta {}");
        event.setArgumentArray(new Object[]{"ES9121000418450200051332"});

        assertThat(converter.convert(event)).contains("[IBAN]").doesNotContain("ES91");
    }

    @Test
    void shouldLeaveCleanMessageUnchanged() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Consumo: 245 kWh");

        assertThat(converter.convert(event)).isEqualTo("Consumo: 245 kWh");
    }

    /**
     * Five bare digits are a latency or a counter here, not a postal code. Scrubbing them turned
     * {@code latencyMs=13040} into {@code latencyMs=[CP]} and ate the five-decimal prices out of
     * a logged prompt — destroying exactly the numbers a log exists to carry.
     */
    @Test
    void shouldKeepFiveDigitNumbersThatAreNotPostalCodes() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Field extraction succeeded [latencyMs=13040, precio=0,15234]");

        assertThat(converter.convert(event))
                .contains("13040").contains("0,15234").doesNotContain("[CP]");
    }
}