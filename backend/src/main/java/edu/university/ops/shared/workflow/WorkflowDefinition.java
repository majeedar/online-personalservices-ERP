package edu.university.ops.shared.workflow;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A named, versioned workflow type, e.g. ABSENCE_APPROVAL v1 (reference data). */
@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinition {

    @Id
    private UUID id;

    private String code;
    private int version;
    private String description;
    private boolean active;

    protected WorkflowDefinition() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getVersion() {
        return version;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
