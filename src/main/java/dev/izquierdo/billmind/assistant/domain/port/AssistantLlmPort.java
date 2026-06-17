package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;

import java.util.List;

public interface AssistantLlmPort {
    ChatResult answer(InvoiceFields invoiceFields, List<RegulatorySnippet> context, String question, List<ConversationMessage> history);
}