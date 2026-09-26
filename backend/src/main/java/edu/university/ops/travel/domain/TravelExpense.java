package edu.university.ops.travel.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One expense of a completed trip (AGENT.md §14.4). No statutory reimbursement engine. */
@Entity
@Table(name = "travel_expense")
public class TravelExpense {

    public enum Type { TRAIN, FLIGHT, HOTEL, TAXI, LOCAL_TRANSPORT, MILEAGE, MEALS, CONFERENCE_FEE, OTHER }

    public enum Status { DRAFT, SUBMITTED, ACCEPTED, REJECTED }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Type expenseType;

    private LocalDate expenseDate;
    private BigDecimal amount;
    private String currency;
    private String description;
    private UUID receiptDocumentId;

    @Enumerated(EnumType.STRING)
    private Status status;

    protected TravelExpense() {
    }

    public TravelExpense(Type type, LocalDate date, BigDecimal amount, String currency, String description) {
        this.id = UUID.randomUUID();
        this.expenseType = type;
        this.expenseDate = date;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = Status.DRAFT;
    }

    void attachReceipt(UUID documentId) {
        this.receiptDocumentId = documentId;
    }

    void submit() {
        this.status = Status.SUBMITTED;
    }

    void reopen() {
        this.status = Status.DRAFT;
    }

    void accept() {
        this.status = Status.ACCEPTED;
    }

    public UUID getId() {
        return id;
    }

    public Type getExpenseType() {
        return expenseType;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public UUID getReceiptDocumentId() {
        return receiptDocumentId;
    }

    public Status getStatus() {
        return status;
    }
}
