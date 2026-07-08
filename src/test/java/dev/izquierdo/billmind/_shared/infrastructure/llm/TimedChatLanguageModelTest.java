package dev.izquierdo.billmind._shared.infrastructure.llm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimedChatLanguageModelTest {

    @Mock
    private ChatModel delegate;

    private ListAppender<ILoggingEvent> logAppender;

    private static final ChatRequest EMPTY_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("test"))
            .build();

    @BeforeEach
    void attachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TimedChatLanguageModel.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TimedChatLanguageModel.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void shouldReturnDelegateResponse() {
        ChatResponse expected = ChatResponse.builder().aiMessage(AiMessage.from("answer")).build();
        when(delegate.chat(any(ChatRequest.class))).thenReturn(expected);

        TimedChatLanguageModel model = new TimedChatLanguageModel(delegate, "fast", "openai", "gpt-4o");

        assertThat(model.chat(EMPTY_REQUEST)).isSameAs(expected);
    }

    @Test
    void shouldLogCostUsdWhenModelIsKnown() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(500, 100))
                .build();
        when(delegate.chat(any(ChatRequest.class))).thenReturn(response);

        new TimedChatLanguageModel(delegate, "fast", "openai", "gpt-4o").chat(EMPTY_REQUEST);

        assertThat(lastLog())
            .contains("costUsd=")
            .contains("tokensIn=500")
            .contains("tokensOut=100");
    }

    @Test
    void shouldNotLogCostUsdWhenModelIsUnknown() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(500, 100))
                .build();
        when(delegate.chat(any(ChatRequest.class))).thenReturn(response);

        new TimedChatLanguageModel(delegate, "fast", "ollama", "llama3.2").chat(EMPTY_REQUEST);

        assertThat(lastLog()).doesNotContain("costUsd=");
    }

    @Test
    void shouldLogProviderRoleAndModel() {
        when(delegate.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        new TimedChatLanguageModel(delegate, "smart", "anthropic", "claude-sonnet-4-6").chat(EMPTY_REQUEST);

        assertThat(lastLog())
            .contains("role=smart")
            .contains("provider=anthropic")
            .contains("model=claude-sonnet-4-6");
    }

    @Test
    void shouldRethrowExceptionFromDelegate() {
        when(delegate.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

        TimedChatLanguageModel model = new TimedChatLanguageModel(delegate, "fast", "openai", "gpt-4o");

        assertThatThrownBy(() -> model.chat(EMPTY_REQUEST))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("timeout");
    }

    @Test
    void shouldLogErrorWhenDelegateFails() {
        when(delegate.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("bad request"));

        TimedChatLanguageModel model = new TimedChatLanguageModel(delegate, "smart", "anthropic", "claude-sonnet-4-6");
        try { model.chat(EMPTY_REQUEST); } catch (Exception ignored) {}

        assertThat(lastLog()).contains("error=IllegalStateException");
    }

    @Test
    void shouldLogLatency() {
        when(delegate.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("ok")).build());

        new TimedChatLanguageModel(delegate, "fast", "openai", "gpt-4o").chat(EMPTY_REQUEST);

        assertThat(lastLog()).containsPattern("latency=\\d+ms");
    }

    @Test
    void shouldFeedTelemetrySinkOnSuccessWithTokensAndCost() {
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("ok"))
                .tokenUsage(new TokenUsage(500, 100))
                .build();
        when(delegate.chat(any(ChatRequest.class))).thenReturn(response);
        CapturingTelemetry sink = new CapturingTelemetry();

        new TimedChatLanguageModel(delegate, "smart", "openai", "gpt-4o", sink).chat(EMPTY_REQUEST);

        assertThat(sink.last).isNotNull();
        assertThat(sink.last.role()).isEqualTo("smart");
        assertThat(sink.last.provider()).isEqualTo("openai");
        assertThat(sink.last.model()).isEqualTo("gpt-4o");
        assertThat(sink.last.tokensIn()).isEqualTo(500);
        assertThat(sink.last.tokensOut()).isEqualTo(100);
        assertThat(sink.last.costUsd()).isNotNull().isPositive();
        assertThat(sink.last.isError()).isFalse();
    }

    @Test
    void shouldFeedTelemetrySinkWithErrorWhenDelegateFails() {
        when(delegate.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("boom"));
        CapturingTelemetry sink = new CapturingTelemetry();

        TimedChatLanguageModel model = new TimedChatLanguageModel(delegate, "fast", "openai", "gpt-4o", sink);
        try { model.chat(EMPTY_REQUEST); } catch (Exception ignored) {}

        assertThat(sink.last).isNotNull();
        assertThat(sink.last.error()).isEqualTo("IllegalStateException");
        assertThat(sink.last.tokensIn()).isNull();
    }

    @Test
    void shouldNotPropagateTelemetryFailureOntoLlmPath() {
        ChatResponse expected = ChatResponse.builder().aiMessage(AiMessage.from("answer")).build();
        when(delegate.chat(any(ChatRequest.class))).thenReturn(expected);
        LlmTelemetry brokenSink = data -> { throw new RuntimeException("exporter down"); };

        TimedChatLanguageModel model = new TimedChatLanguageModel(delegate, "fast", "openai", "gpt-4o", brokenSink);

        assertThat(model.chat(EMPTY_REQUEST)).isSameAs(expected);
    }

    private static final class CapturingTelemetry implements LlmTelemetry {
        private LlmCallData last;

        @Override
        public void record(LlmCallData data) {
            this.last = data;
        }
    }

    private String lastLog() {
        List<ILoggingEvent> events = logAppender.list;
        assertThat(events).isNotEmpty();
        return events.getLast().getFormattedMessage();
    }
}