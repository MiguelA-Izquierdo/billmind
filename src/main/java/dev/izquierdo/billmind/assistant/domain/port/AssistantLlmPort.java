package dev.izquierdo.billmind.assistant.domain.port;

import dev.izquierdo.billmind.assistant.domain.model.ChatResult;
import dev.izquierdo.billmind.assistant.domain.model.RegulatorySnippet;

import java.util.List;

public interface AssistantLlmPort {
    ChatResult answer(String invoiceText, List<RegulatorySnippet> context, String question);
}