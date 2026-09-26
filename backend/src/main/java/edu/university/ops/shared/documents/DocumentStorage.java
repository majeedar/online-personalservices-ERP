package edu.university.ops.shared.documents;

import java.io.IOException;
import java.io.InputStream;

/**
 * Port for binary document content. The prototype stores files in a mounted
 * folder; an S3/MinIO or DMS adapter can replace it (AGENT.md §93).
 */
public interface DocumentStorage {

    /** Stores the content and returns an opaque storage reference. */
    String store(InputStream content) throws IOException;

    InputStream open(String storageReference) throws IOException;
}
