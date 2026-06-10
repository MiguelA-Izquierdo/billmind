package dev.izquierdo.billmind.knowledge.infrastructure.adapter;

import dev.izquierdo.billmind.knowledge.domain.port.DocumentChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OverlapDocumentChunker implements DocumentChunker {

    private final int chunkSize;
    private final int overlap;

    public OverlapDocumentChunker(
            @Value("${knowledge.chunk.size:150}") int chunkSize,
            @Value("${knowledge.chunk.overlap:30}") int overlap) {
        this.chunkSize = chunkSize;
        this.overlap   = overlap;
    }

    @Override
    public List<String> chunk(String text) {
        String[] words  = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            String chunk = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end == words.length) break;
            start = end - overlap;
        }

        return chunks;
    }
}