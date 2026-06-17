package dev.izquierdo.billmind.assistant.application.usecase;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.assistant.application.command.ChatCommand;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.Conversation;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.MessageRole;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;
import dev.izquierdo.billmind.assistant.domain.port.AssistantLlmPort;
import dev.izquierdo.billmind.assistant.domain.port.AssistantRepository;
import dev.izquierdo.billmind.assistant.domain.port.InvoiceContextPort;
import dev.izquierdo.billmind.assistant.domain.port.RegulationSearchPort;
import dev.izquierdo.billmind.invoice.domain.model.Invoice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class ChatUseCase {

    private final AssistantRepository repository;
    private final InvoiceContextPort invoiceContextPort;
    private final RegulationSearchPort regulationSearchPort;
    private final AssistantLlmPort llmPort;
    private final int maxKnowledgeResults;

    public ChatUseCase(
            AssistantRepository repository,
            InvoiceContextPort invoiceContextPort,
            RegulationSearchPort regulationSearchPort,
            AssistantLlmPort llmPort,
            @Value("${knowledge.search.default-max-results:5}") int maxKnowledgeResults) {
        this.repository            = Objects.requireNonNull(repository);
        this.invoiceContextPort    = Objects.requireNonNull(invoiceContextPort);
        this.regulationSearchPort  = Objects.requireNonNull(regulationSearchPort);
        this.llmPort               = Objects.requireNonNull(llmPort);
        this.maxKnowledgeResults   = maxKnowledgeResults;
    }

    public ChatResult execute(ChatCommand command) {
        Conversation conversation = (command.conversationId() != null)
                ? repository.findById(command.conversationId())
                        .orElseGet(() -> Conversation.create(command.sessionId(), command.invoiceId()))
                : Conversation.create(command.sessionId(), command.invoiceId());

        InvoiceFields invoiceFields = command.invoiceId() != null
                ? invoiceContextPort.loadInvoice(command.invoiceId()).map(Invoice::getFields).orElse(null)
                : null;

        List<RegulatorySnippet> regulatoryContext = regulationSearchPort.search(command.message(), maxKnowledgeResults);

        ChatResult result = llmPort.answer(invoiceFields, regulatoryContext, command.message(), conversation.getRecentMessages(6));

        conversation.addMessage(ConversationMessage.create(MessageRole.USER, command.message()));
        conversation.addMessage(ConversationMessage.create(MessageRole.ASSISTANT, result.answer()));
        repository.save(conversation);

        return new ChatResult(conversation.getId(), result.answer(), result.citations());
    }
}