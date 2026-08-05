package dev.izquierdo.billmind._shared.infrastructure.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ThrottleMessagesTest {

    @Test
    void shouldNameTheWaitWhenTheBucketKnowsIt() {
        assertThat(ThrottleMessages.throttled(Duration.ofMinutes(12)))
                .contains("Has agotado tus consultas")
                .contains("Vuelve a intentarlo en 12 minutos.");
    }

    @Test
    void shouldFallBackToAVagueWaitWhenUnknown() {
        assertThat(ThrottleMessages.throttled(Duration.ZERO))
                .endsWith("Inténtalo de nuevo más tarde.");
    }

    @Test
    void shouldRoundSubMinuteWaitsUpToOneMinute() {
        assertThat(ThrottleMessages.humanize(Duration.ofSeconds(8))).isEqualTo("un minuto");
        assertThat(ThrottleMessages.humanize(Duration.ofSeconds(60))).isEqualTo("un minuto");
    }

    @Test
    void shouldRoundPartialMinutesUpSoTheWaitIsNeverUnderstated() {
        assertThat(ThrottleMessages.humanize(Duration.ofSeconds(61))).isEqualTo("2 minutos");
        assertThat(ThrottleMessages.humanize(Duration.ofSeconds(701))).isEqualTo("12 minutos");
    }

    @Test
    void shouldSwitchToHoursBeyondSixtyMinutes() {
        assertThat(ThrottleMessages.humanize(Duration.ofMinutes(60))).isEqualTo("una hora");
        assertThat(ThrottleMessages.humanize(Duration.ofMinutes(150))).isEqualTo("3 horas");
    }

    @Test
    void shouldAppendTheWaitToTheLlmMessage() {
        assertThat(ThrottleMessages.llmThrottled("Muchas consultas.", Duration.ofMinutes(2)))
                .isEqualTo("Muchas consultas. Vuelve a intentarlo en 2 minutos.");
    }

    @Test
    void shouldStillAdviseRetryingWhenTheProviderNamedNoWait() {
        assertThat(ThrottleMessages.llmThrottled("Muchas consultas.", Duration.ZERO))
                .isEqualTo("Muchas consultas. Inténtalo de nuevo más tarde.");
    }
}