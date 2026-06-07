package dev.izquierdo.billmind.assistant.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceContextPort {
    Optional<String> loadRawText(UUID invoiceId);
}