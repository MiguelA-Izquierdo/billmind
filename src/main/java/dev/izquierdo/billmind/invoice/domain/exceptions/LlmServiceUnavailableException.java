package dev.izquierdo.billmind.invoice.domain.exceptions;

public class LlmServiceUnavailableException extends RuntimeException {

    public LlmServiceUnavailableException(Throwable cause) {
        super("El servicio de procesamiento inteligente no está disponible temporalmente. Por favor, inténtalo de nuevo en unos minutos.", cause);
    }
}