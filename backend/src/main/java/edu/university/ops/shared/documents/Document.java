package edu.university.ops.shared.documents;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Document metadata (AGENT.md §14.5). The content lives in {@link DocumentStorage}. */
@Entity
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    private String businessObjectType;
    private UUID businessObjectId;
    private String documentType;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    private String storageReference;
    private UUID uploadedBy;
    private Instant uploadedAt;

    protected Document() {
    }

    Document(String businessObjectType, UUID businessObjectId, String documentType, String fileName,
             String contentType, long sizeBytes, String storageReference, UUID uploadedBy, Instant uploadedAt) {
        this.id = UUID.randomUUID();
        this.businessObjectType = businessObjectType;
        this.businessObjectId = businessObjectId;
        this.documentType = documentType;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageReference = storageReference;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getBusinessObjectType() {
        return businessObjectType;
    }

    public UUID getBusinessObjectId() {
        return businessObjectId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageReference() {
        return storageReference;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
