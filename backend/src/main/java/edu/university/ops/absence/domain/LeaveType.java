package edu.university.ops.absence.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Configurable leave type (AGENT.md §13.1). */
@Entity
@Table(name = "leave_type")
public class LeaveType {

    @Id
    private UUID id;

    private String code;
    private String name;
    private boolean deductsEntitlement;
    private boolean requiresApproval;
    private boolean creditsWorkingTime;
    private boolean attachmentRequired;
    private boolean active;

    protected LeaveType() {
    }

    /** For tests and calculations outside persistence. */
    public LeaveType(UUID id, String code, String name, boolean deductsEntitlement, boolean requiresApproval,
                     boolean creditsWorkingTime, boolean attachmentRequired, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.deductsEntitlement = deductsEntitlement;
        this.requiresApproval = requiresApproval;
        this.creditsWorkingTime = creditsWorkingTime;
        this.attachmentRequired = attachmentRequired;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isDeductsEntitlement() {
        return deductsEntitlement;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public boolean isCreditsWorkingTime() {
        return creditsWorkingTime;
    }

    public boolean isAttachmentRequired() {
        return attachmentRequired;
    }

    public boolean isActive() {
        return active;
    }
}
