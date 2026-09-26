package edu.university.ops.shared.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface DocumentRepository extends Repository<Document, UUID> {

    Document save(Document document);

    Optional<Document> findById(UUID id);

    List<Document> findByBusinessObjectTypeAndBusinessObjectIdOrderByUploadedAt(String type, UUID id);

    boolean existsByBusinessObjectTypeAndBusinessObjectId(String type, UUID id);

    void delete(Document document);
}
