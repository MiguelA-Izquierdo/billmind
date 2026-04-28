package com.demo.billmind.invoice.domain.model;

import java.util.Objects;
import java.util.UUID;

public class Invoice {
    private final UUID id;
    private final String fileName;

    public Invoice(UUID id, String fileName) {
        this.id = Objects.requireNonNull(id, "Invoice ID cannot be null");
        Objects.requireNonNull(fileName, "File name cannot be null");
        if (fileName.isBlank()) throw new IllegalArgumentException("File name cannot be blank");
        this.fileName = fileName;
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }
}