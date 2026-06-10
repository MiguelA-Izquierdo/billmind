package dev.izquierdo.billmind.invoice.domain.model;

import dev.izquierdo.billmind._shared.domain.model.InvoiceType;
import dev.izquierdo.billmind._shared.domain.model.fields.InvoiceFields;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Invoice {
    private final UUID id;
    private final String fileName;
    private final Instant uploadedAt;
    private final InvoiceType supplyType;
    private final String provider;
    private final UUID sessionId;
    private final InvoiceFields fields;
    private final String rawTextRedacted;

    private Invoice(Builder builder) {
        this.id              = Objects.requireNonNull(builder.id, "Invoice ID cannot be null");
        Objects.requireNonNull(builder.fileName, "File name cannot be null");
        if (builder.fileName.isBlank()) throw new IllegalArgumentException("File name cannot be blank");
        this.fileName        = builder.fileName;
        this.uploadedAt      = builder.uploadedAt != null ? builder.uploadedAt : Instant.now();
        this.supplyType      = builder.supplyType;
        this.provider        = builder.provider;
        this.sessionId       = builder.sessionId;
        this.fields          = builder.fields;
        this.rawTextRedacted = builder.rawTextRedacted;
    }

    public static Builder builder(UUID id, String fileName) {
        return new Builder(id, fileName);
    }

    public Builder toBuilder() {
        return new Builder(id, fileName)
            .uploadedAt(uploadedAt)
            .supplyType(supplyType)
            .provider(provider)
            .sessionId(sessionId)
            .fields(fields)
            .rawTextRedacted(rawTextRedacted);
    }

    public Invoice withClassification(InvoiceClassification classification) {
        return toBuilder()
            .supplyType(classification.getType())
            .provider(classification.getCompany())
            .build();
    }

    public Invoice withSessionId(UUID sessionId) {
        return toBuilder().sessionId(sessionId).build();
    }

    public Invoice withExtractedData(InvoiceFields extractedFields, String redactedText) {
        return toBuilder()
            .fields(extractedFields)
            .rawTextRedacted(redactedText)
            .build();
    }

    @Override
    public String toString() {
        String textPreview = rawTextRedacted != null && rawTextRedacted.length() > 200
                ? rawTextRedacted.substring(0, 200) + "..."
                : rawTextRedacted;
        return "Invoice{id=" + id +
               ", fileName='" + fileName + '\'' +
               ", uploadedAt=" + uploadedAt +
               ", supplyType=" + supplyType +
               ", provider='" + provider + '\'' +
               ", sessionId=" + sessionId +
               ", fields=" + fields +
               ", rawTextRedacted='" + textPreview + '\'' +
               '}';
    }

    public UUID getId()                        { return id; }
    public String getFileName()                { return fileName; }
    public Instant getUploadedAt()             { return uploadedAt; }
    public InvoiceType getSupplyType()         { return supplyType; }
    public String getProvider()                { return provider; }
    public UUID getSessionId()                 { return sessionId; }
    public InvoiceFields getFields()           { return fields; }
    public String getRawTextRedacted()         { return rawTextRedacted; }

    public static final class Builder {
        private final UUID id;
        private final String fileName;
        private Instant uploadedAt;
        private InvoiceType supplyType;
        private String provider;
        private UUID sessionId;
        private InvoiceFields fields;
        private String rawTextRedacted;

        private Builder(UUID id, String fileName) {
            this.id       = id;
            this.fileName = fileName;
        }

        public Builder uploadedAt(Instant uploadedAt)             { this.uploadedAt      = uploadedAt;      return this; }
        public Builder supplyType(InvoiceType supplyType)         { this.supplyType      = supplyType;      return this; }
        public Builder provider(String provider)                  { this.provider        = provider;        return this; }
        public Builder sessionId(UUID sessionId)                  { this.sessionId       = sessionId;       return this; }
        public Builder fields(InvoiceFields fields)               { this.fields          = fields;          return this; }
        public Builder rawTextRedacted(String rawTextRedacted)    { this.rawTextRedacted = rawTextRedacted;  return this; }
        public Invoice build()                                    { return new Invoice(this); }
    }
}