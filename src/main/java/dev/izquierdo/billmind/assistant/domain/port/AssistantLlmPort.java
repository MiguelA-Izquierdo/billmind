package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind.assistant.domain.model.ChatContext;
import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.ConversationMessage;

import java.util.List;

public interface AssistantLlmPort {
    ChatResult answer(ChatContext context, String question, List<ConversationMessage> history);
}