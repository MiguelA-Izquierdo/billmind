package dev.izquierdo.billmind.assistant.domain.exceptions;

import java.util.UUID;

public class ConversationNotFoundException extends RuntimeException {
    public ConversationNotFoundException(UUID id) {
        super("Conversación no encontrada: " + id);
    }
}