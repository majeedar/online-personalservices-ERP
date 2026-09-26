package edu.university.ops.shared.documents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stores each document as a file named by a random UUID; original names are metadata only. */
@Component
class FileSystemDocumentStorage implements DocumentStorage {

    private static final Pattern REFERENCE = Pattern.compile("[0-9a-f-]{36}");

    private final Path root;

    FileSystemDocumentStorage(@Value("${ops.documents.path:./data/documents}") Path root) throws IOException {
        this.root = Files.createDirectories(root.toAbsolutePath().normalize());
    }

    @Override
    public String store(InputStream content) throws IOException {
        String reference = UUID.randomUUID().toString();
        Files.copy(content, root.resolve(reference));
        return reference;
    }

    @Override
    public InputStream open(String storageReference) throws IOException {
        // References are generated above; anything else is rejected to rule out path traversal.
        if (!REFERENCE.matcher(storageReference).matches()) {
            throw new IOException("Invalid storage reference");
        }
        return Files.newInputStream(root.resolve(storageReference));
    }
}
