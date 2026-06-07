package dev.izquierdo.billmind.knowledge.domain.port;

import java.util.List;

public interface DocumentChunker {

    List<String> chunk(String text);
}