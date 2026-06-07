package dev.izquierdo.billmind.assistant.application.command;

import dev.izquierdo.billmind._shared.application.command.Command;

import java.util.UUID;

public record ChatCommand(
    UUID sessionId,
    UUID invoiceId,
    String message
) implements Command {
    public ChatCommand {
        if (sessionId == null) throw new IllegalArgumentException("sessionId cannot be null");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message cannot be blank");
    }
}