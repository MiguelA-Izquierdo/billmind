package dev.izquierdo.billmind.metrics.infrastructure.event;

import dev.izquierdo.billmind._shared.domain.event.handle.DomainEventHandler;
import dev.izquierdo.billmind.assistant.domain.event.AssistantQuestionAnswered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Metrics reaction to {@link AssistantQuestionAnswered}: chat engagement plus the
 * knowledge-base coverage gap ({@code citationCount == 0}).
 *
 * <p>Placeholder implementation — logs only until the metrics domain lands.
 */
@Component
public class AssistantQuestionAnsweredMetricsHandler implements DomainEventHandler<AssistantQuestionAnswered> {

    private static final Logger log = LoggerFactory.getLogger(AssistantQuestionAnsweredMetricsHandler.class);

    @Override
    public void handle(AssistantQuestionAnswered event) {
        AssistantQuestionAnswered.Payload payload = event.getData();
        log.info("[metrics] assistant question answered: citationCount={}, questionLength={}",
                payload.citationCount(), payload.questionLength());
    }

    @Override
    public Class<AssistantQuestionAnswered> supportsEventType() {
        return AssistantQuestionAnswered.class;
    }
}