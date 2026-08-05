package dev.izquierdo.billmind._shared.infrastructure.llm;

import dev.izquierdo.billmind._shared.domain.exceptions.LlmRateLimitedException;
import dev.izquierdo.billmind._shared.domain.exceptions.LlmServiceUnavailableException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.RateLimitException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmFailuresTest {

    @Test
    void shouldTranslateRateLimitExceptionWhenProviderThrottles() {
        RuntimeException translated = LlmFailures.translate(new RateLimitException("slow down"));

        assertThat(translated).isInstanceOf(LlmRateLimitedException.class);
    }

    @Test
    void shouldTranslateHttp429WhenNestedTwoLevelsDeep() {
        Throwable failure = new RuntimeException("call failed",
                new IllegalStateException("wrapped", new HttpException(429, "rate_limit_exceeded")));

        assertThat(LlmFailures.translate(failure)).isInstanceOf(LlmRateLimitedException.class);
    }

    @Test
    void shouldReadRetryAfterFromGroqStyleBody() {
        HttpException body = new HttpException(429,
                "{\"error\":{\"message\":\"Rate limit reached. Please try again in 7.66s\"}}");

        LlmRateLimitedException translated = (LlmRateLimitedException) LlmFailures.translate(body);

        assertThat(translated.retryAfter()).isEqualTo(Duration.ofSeconds(8));
    }

    @Test
    void shouldReadRetryAfterWithMinutesAndSeconds() {
        HttpException body = new HttpException(429, "Please try again in 2m30.5s");

        LlmRateLimitedException translated = (LlmRateLimitedException) LlmFailures.translate(body);

        assertThat(translated.retryAfter()).isEqualTo(Duration.ofSeconds(151));
    }

    /** A daily quota is exhausted for the rest of the day, so Groq answers in hours, not seconds. */
    @Test
    void shouldReadRetryAfterFromGroqDailyQuotaMessage() {
        HttpException body = new HttpException(429, "{\"error\":{\"message\":\"Rate limit reached for "
                + "model `llama-3.3-70b-versatile` in organization `org_01` service tier `on_demand` on "
                + "tokens per day (TPD): Limit 100000, Used 100000, Requested 1200. "
                + "Please try again in 11h2m30.5s.\"}}");

        LlmRateLimitedException translated = (LlmRateLimitedException) LlmFailures.translate(body);

        assertThat(translated.retryAfter()).isEqualTo(Duration.ofHours(11).plusMinutes(2).plusSeconds(31));
    }

    @Test
    void shouldReadRetryAfterWhenProviderNamesOnlyHours() {
        HttpException body = new HttpException(429, "Please try again in 2h");

        LlmRateLimitedException translated = (LlmRateLimitedException) LlmFailures.translate(body);

        assertThat(translated.retryAfter()).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void shouldReadRetryAfterFromHeaderStyleMessage() {
        HttpException body = new HttpException(429, "throttled, retry-after: 30");

        LlmRateLimitedException translated = (LlmRateLimitedException) LlmFailures.translate(body);

        assertThat(translated.retryAfter()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldReportUnknownRetryAfterWhenProviderNamesNoWait() {
        LlmRateLimitedException translated =
                (LlmRateLimitedException) LlmFailures.translate(new RateLimitException("too many requests"));

        assertThat(translated.hasRetryAfter()).isFalse();
        assertThat(translated.retryAfter()).isZero();
    }

    @Test
    void shouldTranslateServerErrorToUnavailable() {
        assertThat(LlmFailures.translate(new InternalServerException("boom")))
                .isInstanceOf(LlmServiceUnavailableException.class);
        assertThat(LlmFailures.translate(new HttpException(503, "upstream down")))
                .isInstanceOf(LlmServiceUnavailableException.class);
    }

    @Test
    void shouldTranslateConnectionFailureToUnavailable() {
        Throwable failure = new RuntimeException("connect", new IOException("connection refused"));

        assertThat(LlmFailures.translate(failure)).isInstanceOf(LlmServiceUnavailableException.class);
    }

    @Test
    void shouldLeaveInvalidRequestUntouchedSoTheAgentCanRecover() {
        InvalidRequestException malformedToolCall = new InvalidRequestException("bad tool call");

        assertThat(LlmFailures.translate(malformedToolCall)).isSameAs(malformedToolCall);
    }

    @Test
    void shouldLeaveUnrelatedRuntimeExceptionUntouched() {
        IllegalStateException bug = new IllegalStateException("our own bug");

        assertThat(LlmFailures.translate(bug)).isSameAs(bug);
    }

    @Test
    void shouldNotClassifyByWordsAloneWhenFailureIsNotAnHttpError() {
        // A model answering a question about tariffs can write "rate limit" into plain prose.
        IllegalStateException notThrottled = new IllegalStateException("the rate limit of the tariff is 4.6 kW");

        assertThat(LlmFailures.translate(notThrottled)).isSameAs(notThrottled);
    }

    @Test
    void shouldSurviveACyclicCauseChain() {
        RuntimeException outer = new RuntimeException("outer");
        RuntimeException inner = new RuntimeException("inner", outer);
        outer.initCause(inner);

        assertThat(LlmFailures.translate(outer)).isSameAs(outer);
    }

    @Test
    void shouldWrapCheckedFailureAsUnavailable() {
        assertThat(LlmFailures.translate(new Exception("checked")))
                .isInstanceOf(LlmServiceUnavailableException.class);
    }

    @Test
    void shouldKeepOriginalFailureAsCauseForTheLogs() {
        RateLimitException original = new RateLimitException("slow down");

        assertThat(LlmFailures.translate(original)).hasCause(original);
    }
}