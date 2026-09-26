package edu.university.ops.absence.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Annual entitlement of one leave type (AGENT.md §13.4).
 *
 * <p>Deterministic ledger: submitted requests <em>reserve</em> days, approval moves
 * them to <em>used</em>, rejection/withdrawal releases them, cancelling an approved
 * absence restores them. {@link #remainingDays()} is always derived, never stored.
 */
@Entity
@Table(name = "leave_entitlement")
public class LeaveEntitlement {

    @Id
    private UUID id;

    private UUID employeeId;
    private int year;
    private UUID leaveTypeId;
    private BigDecimal baseDays;
    private BigDecimal carryOverDays;
    private BigDecimal additionalDays;
    private BigDecimal usedDays;
    private BigDecimal reservedDays;
    private LocalDate expiryDate;

    @Version
    private Long version;

    protected LeaveEntitlement() {
    }

    public LeaveEntitlement(UUID employeeId, int year, UUID leaveTypeId, BigDecimal baseDays,
                            BigDecimal carryOverDays, BigDecimal additionalDays, LocalDate expiryDate) {
        this.id = UUID.randomUUID();
        this.employeeId = employeeId;
        this.year = year;
        this.leaveTypeId = leaveTypeId;
        this.baseDays = baseDays;
        this.carryOverDays = carryOverDays;
        this.additionalDays = additionalDays;
        this.usedDays = BigDecimal.ZERO;
        this.reservedDays = BigDecimal.ZERO;
        this.expiryDate = expiryDate;
    }

    public BigDecimal totalDays() {
        return baseDays.add(carryOverDays).add(additionalDays);
    }

    public BigDecimal remainingDays() {
        return totalDays().subtract(usedDays).subtract(reservedDays);
    }

    public boolean covers(BigDecimal days) {
        return remainingDays().compareTo(days) >= 0;
    }

    public void reserve(BigDecimal days) {
        reservedDays = reservedDays.add(days);
    }

    public void releaseReservation(BigDecimal days) {
        reservedDays = reservedDays.subtract(days).max(BigDecimal.ZERO);
    }

    /** Approval: the reservation becomes usage. */
    public void consumeReservation(BigDecimal days) {
        releaseReservation(days);
        usedDays = usedDays.add(days);
    }

    /** Direct usage without prior reservation (types without approval). */
    public void use(BigDecimal days) {
        usedDays = usedDays.add(days);
    }

    /** Cancellation of an approved absence. */
    public void restore(BigDecimal days) {
        usedDays = usedDays.subtract(days).max(BigDecimal.ZERO);
    }

    /** Recalculation from the authoritative absence days (batch, AGENT.md §27.5). */
    public void resetCounters(BigDecimal used, BigDecimal reserved) {
        this.usedDays = used;
        this.reservedDays = reserved;
    }

    /** Adjust the base entitlement, e.g. after a change of the work schedule. */
    public void updateBase(BigDecimal baseDays) {
        this.baseDays = baseDays;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public int getYear() {
        return year;
    }

    public UUID getLeaveTypeId() {
        return leaveTypeId;
    }

    public BigDecimal getBaseDays() {
        return baseDays;
    }

    public BigDecimal getCarryOverDays() {
        return carryOverDays;
    }

    public BigDecimal getAdditionalDays() {
        return additionalDays;
    }

    public BigDecimal getUsedDays() {
        return usedDays;
    }

    public BigDecimal getReservedDays() {
        return reservedDays;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
