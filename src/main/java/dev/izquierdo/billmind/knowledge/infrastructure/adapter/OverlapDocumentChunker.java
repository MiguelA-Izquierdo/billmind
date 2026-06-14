package dev.izquierdo.billmind.knowledge.infrastructure.adapter;

import dev.izquierdo.billmind.knowledge.domain.port.DocumentChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class OverlapDocumentChunker implements DocumentChunker {

    private static final int MIN_CHUNK_WORDS = 10;

    private final int chunkSize;
    private final int overlap;

    public OverlapDocumentChunker(
            @Value("${knowledge.chunk.size:150}") int chunkSize,
            @Value("${knowledge.chunk.overlap:30}") int overlap) {
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException(
                    "overlap must be < chunkSize, but got overlap=" + overlap + " chunkSize=" + chunkSize);
        }
        this.chunkSize = chunkSize;
        this.overlap   = overlap;
    }

    @Override
    public List<String> chunk(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");

        String[] words = normalized.split(" ");
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            int wordCount = end - start;
            if (wordCount >= MIN_CHUNK_WORDS) {
                chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            } else if (!chunks.isEmpty()) {
                chunks.set(chunks.size() - 1,
                        chunks.getLast() + " " + String.join(" ", Arrays.copyOfRange(words, start, end)));
            } else {
                chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            }
            if (end == words.length) break;
            start = end - overlap;
        }

        return chunks;
    }
}