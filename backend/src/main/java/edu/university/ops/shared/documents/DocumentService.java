package edu.university.ops.shared.documents;

import edu.university.ops.shared.audit.AuditService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Attach documents to business objects. Authorization is the caller's job: the
 * owning module's controller checks access to the business object first.
 */
@Service
@Transactional
public class DocumentService {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/png", "image/jpeg");

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final AuditService audit;
    private final Clock clock;

    DocumentService(DocumentRepository documents, DocumentStorage storage, AuditService audit, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    public Document attach(String businessObjectType, UUID businessObjectId, String documentType,
                           MultipartFile file, UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID, "The file is empty.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID, "Files may be at most 10 MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.DOCUMENT_INVALID, "Only PDF, PNG and JPEG files are accepted.");
        }
        String fileName = sanitize(file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            String reference = storage.store(in);
            Document doc = documents.save(new Document(businessObjectType, businessObjectId,
                    documentType == null ? "OTHER" : documentType, fileName, contentType, file.getSize(), reference,
                    uploadedBy, Instant.now(clock)));
            audit.record("DOCUMENT_ATTACHED", businessObjectType, businessObjectId, null,
                    Map.of("documentId", doc.getId(), "documentType", doc.getDocumentType(),
                            "size", doc.getSizeBytes()));
            return doc;
        } catch (IOException e) {
            throw new UncheckedIOException("Document could not be stored", e);
        }
    }

    @Transactional(readOnly = true)
    public List<Document> documentsOf(String businessObjectType, UUID businessObjectId) {
        return documents.findByBusinessObjectTypeAndBusinessObjectIdOrderByUploadedAt(businessObjectType,
                businessObjectId);
    }

    @Transactional(readOnly = true)
    public boolean hasDocuments(String businessObjectType, UUID businessObjectId) {
        return documents.existsByBusinessObjectTypeAndBusinessObjectId(businessObjectType, businessObjectId);
    }

    /** Loads a document that must belong to the given business object. */
    @Transactional(readOnly = true)
    public Document find(String businessObjectType, UUID businessObjectId, UUID documentId) {
        return documents.findById(documentId)
                .filter(d -> d.getBusinessObjectType().equals(businessObjectType)
                        && d.getBusinessObjectId().equals(businessObjectId))
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Document"));
    }

    /**
     * Retention: deletes all documents of a business object, content first, then
     * metadata. Callers must first clear references to them (e.g. expense receipts).
     */
    public int deleteAll(String businessObjectType, UUID businessObjectId) {
        int deleted = 0;
        for (Document d : documentsOf(businessObjectType, businessObjectId)) {
            try {
                storage.delete(d.getStorageReference());
            } catch (IOException e) {
                throw new UncheckedIOException("Document content could not be deleted", e);
            }
            documents.delete(d);
            deleted++;
        }
        return deleted;
    }

    public InputStream open(Document document) {
        try {
            return storage.open(document.getStorageReference());
        } catch (IOException e) {
            throw new UncheckedIOException("Document content unavailable", e);
        }
    }

    private static String sanitize(String name) {
        String normalized = name == null ? "document" : name.replace('\\', '/');
        String base = StringUtils.getFilename(normalized);
        String cleaned = base == null || base.isBlank() ? "document" : base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }
}
