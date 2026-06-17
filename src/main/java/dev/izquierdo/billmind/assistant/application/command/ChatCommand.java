package dev.izquierdo.billmind.assistant.application.command;

import dev.izquierdo.billmind._shared.application.command.Command;

import java.util.UUID;

public record ChatCommand(
    UUID sessionId,
    UUID invoiceId,
    UUID conversationId,
    String message
) implements Command {
    private static final int MAX_MESSAGE_LENGTH = 500;

    public ChatCommand {
        if (sessionId == null) throw new IllegalArgumentException("sessionId cannot be null");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("El mensaje no puede estar vacío");
        if (message.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("El mensaje no puede superar los " + MAX_MESSAGE_LENGTH + " caracteres");
    }
}