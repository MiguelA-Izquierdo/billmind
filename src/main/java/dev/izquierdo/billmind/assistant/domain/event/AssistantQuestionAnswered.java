package dev.izquierdo.billmind.assistant.domain.event;

import dev.izquierdo.billmind._shared.domain.event.BaseDomainEvent;

import java.util.UUID;

/**
 * Emitted once the assistant has produced a complete answer to a user question.
 *
 * <p>Metrics value: chat engagement (questions per session / conversation) plus a
 * knowledge-base coverage signal — {@code citationCount == 0} means the answer cited no
 * regulatory document, i.e. a potential gap in the KB. This is a business/product signal,
 * distinct from the per-call latency/token/cost telemetry already captured at the
 * infrastructure level by {@code TimedChatLanguageModel}.
 */
public final class AssistantQuestionAnswered extends BaseDomainEvent<AssistantQuestionAnswered.Payload> {

    public static final String EVENT_NAME = "assistant.question-answered";

    public AssistantQuestionAnswered(Payload data) {
        super(data);
    }

    public static AssistantQuestionAnswered of(UUID conversationId, UUID sessionId, UUID invoiceId,
                                               int questionLength, int citationCount) {
        return new AssistantQuestionAnswered(
                new Payload(conversationId, sessionId, invoiceId, questionLength, citationCount));
    }

    @Override
    public String eventName() {
        return EVENT_NAME;
    }

    @Override
    public String getLogMessage() {
        Payload p = getData();
        return "Assistant question answered: conversationId=" + p.conversationId()
                + ", sessionId=" + p.sessionId()
                + ", invoiceId=" + p.invoiceId()
                + ", questionLength=" + p.questionLength()
                + ", citationCount=" + p.citationCount();
    }

    /**
     * Carries only numeric/id signals — never the question or answer text — so downstream
     * metrics handlers stay PII-free.
     */
    public record Payload(
            UUID conversationId,
            UUID sessionId,
            UUID invoiceId,
            int questionLength,
            int citationCount
    ) {
    }
}