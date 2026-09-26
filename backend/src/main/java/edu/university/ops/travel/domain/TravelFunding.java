package edu.university.ops.travel.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One share of a split-funded trip: by percentage or by amount (AGENT.md §14.3). */
@Entity
@Table(name = "travel_funding")
public class TravelFunding {

    @Id
    private UUID id;

    private UUID fundingSourceId;
    private BigDecimal percentage;
    private BigDecimal amount;

    protected TravelFunding() {
    }

    public TravelFunding(UUID fundingSourceId, BigDecimal percentage, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.fundingSourceId = fundingSourceId;
        this.percentage = percentage;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFundingSourceId() {
        return fundingSourceId;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
