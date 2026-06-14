package dev.izquierdo.billmind.knowledge.domain.exceptions;

public class EmptyDocumentException extends RuntimeException {

    public EmptyDocumentException(String source) {
        super("El documento no contiene contenido procesable: " + source);
    }
}