package edu.university.ops.travel.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A budget a trip can be charged to (AGENT.md §14.3). */
@Entity
@Table(name = "funding_source")
public class FundingSource {

    @Id
    private UUID id;

    private String costCentre;
    private String projectCode;
    private String fundCode;
    private String description;
    private boolean active;

    protected FundingSource() {
    }

    public UUID getId() {
        return id;
    }

    public String getCostCentre() {
        return costCentre;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getFundCode() {
        return fundCode;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }
}
