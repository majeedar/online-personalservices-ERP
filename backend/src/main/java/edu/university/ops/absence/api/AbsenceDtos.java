package edu.university.ops.absence.api;

import edu.university.ops.absence.application.AbsenceRules;
import edu.university.ops.absence.application.AbsenceService;
import edu.university.ops.absence.domain.AbsenceDay;
import edu.university.ops.absence.domain.AbsenceDayCalculator.CalculatedDay;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.absence.domain.LeaveEntitlement;
import edu.university.ops.absence.domain.LeaveType;
import edu.university.ops.shared.documents.Document;
import edu.university.ops.shared.workflow.WorkflowViews.InstanceHistory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response DTOs of the absence API (AGENT.md §76). */
final class AbsenceDtos {

    private AbsenceDtos() {
    }

    record AbsenceRequestBody(@NotNull UUID leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                              UUID representativeId, @Size(max = 1000) String comment) {
        AbsenceService.AbsenceInput toInput() {
            return new AbsenceService.AbsenceInput(leaveTypeId, startDate, endDate, representativeId, comment);
        }
    }

    record DecisionBody(@Size(max = 1000) String comment) {
    }

    record LeaveTypeResponse(UUID id, String code, String name, boolean deductsEntitlement, boolean requiresApproval,
                             boolean creditsWorkingTime, boolean attachmentRequired) {
        static LeaveTypeResponse of(LeaveType t) {
            return new LeaveTypeResponse(t.getId(), t.getCode(), t.getName(), t.isDeductsEntitlement(),
                    t.isRequiresApproval(), t.isCreditsWorkingTime(), t.isAttachmentRequired());
        }
    }

    record DayResponse(LocalDate date, AbsenceDay.Kind kind, int plannedMinutes, int creditedMinutes,
                       BigDecimal entitlementDeduction) {
        static DayResponse of(AbsenceDay d) {
            return new DayResponse(d.getDate(), d.getDayKind(), d.getPlannedMinutes(), d.getCreditedMinutes(),
                    d.getEntitlementDeduction());
        }

        static DayResponse of(CalculatedDay d) {
            return new DayResponse(d.date(), d.kind(), d.plannedMinutes(), d.creditedMinutes(),
                    d.entitlementDeduction());
        }
    }

    record IssueResponse(String code, String message) {
        static IssueResponse of(AbsenceRules.Issue i) {
            return new IssueResponse(i.code().name(), i.message());
        }
    }

    record PreviewResponse(List<DayResponse> days, long workingDays, BigDecimal deduction, BigDecimal currentBalance,
                           BigDecimal projectedBalance, List<IssueResponse> issues) {
        static PreviewResponse of(AbsenceService.Preview p) {
            return new PreviewResponse(p.days().stream().map(DayResponse::of).toList(), p.workingDays(),
                    p.deduction(), p.currentBalance(), p.projectedBalance(),
                    p.issues().stream().map(IssueResponse::of).toList());
        }
    }

    record PersonRef(UUID id, String displayName) {
    }

    record AbsenceSummaryResponse(UUID id, LeaveTypeResponse leaveType, LocalDate startDate, LocalDate endDate,
                                  AbsenceStatus status, long workingDays, BigDecimal deduction, Instant submittedAt) {
        static AbsenceSummaryResponse of(AbsenceRequest r, LeaveType t) {
            return new AbsenceSummaryResponse(r.getId(), LeaveTypeResponse.of(t), r.getStartDate(), r.getEndDate(),
                    r.getStatus(), r.workingDays(), r.totalDeduction(), r.getSubmittedAt());
        }
    }

    record DocumentResponse(UUID id, String documentType, String fileName, String contentType, long sizeBytes,
                            Instant uploadedAt) {
        static DocumentResponse of(Document d) {
            return new DocumentResponse(d.getId(), d.getDocumentType(), d.getFileName(), d.getContentType(),
                    d.getSizeBytes(), d.getUploadedAt());
        }
    }

    /** What the caller may do now; the server enforces the same rules again on each action. */
    record AllowedActions(boolean edit, boolean submit, boolean cancel, boolean decide, UUID taskId,
                          boolean decideAsDelegate) {
    }

    record AbsenceResponse(UUID id, PersonRef employee, LeaveTypeResponse leaveType, LocalDate startDate,
                           LocalDate endDate, PersonRef representative, String comment, AbsenceStatus status,
                           long workingDays, BigDecimal deduction, Instant createdAt, Instant submittedAt,
                           List<DayResponse> days, List<InstanceHistory> history, List<DocumentResponse> documents,
                           AllowedActions actions, Instant anonymisedAt) {
    }

    record LeaveBalanceResponse(String leaveTypeCode, String leaveTypeName, int year, BigDecimal baseDays,
                                BigDecimal carryOverDays, BigDecimal additionalDays, BigDecimal usedDays,
                                BigDecimal reservedDays, BigDecimal remainingDays, LocalDate carryOverExpiry) {
        static LeaveBalanceResponse of(LeaveEntitlement e, LeaveType t) {
            return new LeaveBalanceResponse(t.getCode(), t.getName(), e.getYear(), e.getBaseDays(),
                    e.getCarryOverDays(), e.getAdditionalDays(), e.getUsedDays(), e.getReservedDays(),
                    e.remainingDays(), e.getExpiryDate());
        }
    }

    /** Team view: the leave type is deliberately omitted (health data, AGENT.md §82). */
    record TeamAbsenceResponse(UUID requestId, PersonRef employee, LocalDate startDate, LocalDate endDate,
                               AbsenceStatus status, long workingDays) {
    }
}
