package dev.izquierdo.billmind.invoice.infrastructure.config.chat;

import dev.langchain4j.model.chat.ChatModel;

/**
 * Builds a {@link ChatModel} for a given model name, with the active provider's credentials
 * and endpoint already bound. Exactly one implementation is in context at a time (selected by
 * {@code llm.provider}); {@code ChatModelRolesConfig} calls it once per role, so the fast and
 * smart roles can run different models on the same provider.
 */
@FunctionalInterface
public interface ChatModelFactory {

    ChatModel create(String modelName);
}