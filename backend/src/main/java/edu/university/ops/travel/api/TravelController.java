package edu.university.ops.travel.api;

import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.documents.Document;
import edu.university.ops.shared.documents.DocumentService;
import edu.university.ops.shared.integration.OutboxEvent;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowViews.InstanceHistory;
import edu.university.ops.shared.workflow.WorkflowViews.TaskView;
import edu.university.ops.travel.application.TravelQueryService;
import edu.university.ops.travel.application.TravelService;
import edu.university.ops.travel.domain.FundingSource;
import edu.university.ops.travel.domain.TravelExpense;
import edu.university.ops.travel.domain.TravelFunding;
import edu.university.ops.travel.domain.TravelRequest;
import edu.university.ops.travel.domain.TravelStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Travel API (AGENT.md §36). */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Travel")
class TravelController {

    private final TravelService travel;
    private final TravelQueryService queries;
    private final DocumentService documents;
    private final PersonDirectory persons;
    private final Clock clock;

    TravelController(TravelService travel, TravelQueryService queries, DocumentService documents,
                     PersonDirectory persons, Clock clock) {
        this.clock = clock;
        this.travel = travel;
        this.queries = queries;
        this.documents = documents;
        this.persons = persons;
    }

    // ------------------------------------------------------------------ DTOs

    record FundingBody(@NotNull UUID fundingSourceId, @DecimalMin("0") BigDecimal percentage,
                       @DecimalMin("0") BigDecimal amount) {
    }

    record TravelBody(@NotBlank @Size(max = 500) String purpose, @NotBlank @Size(max = 100) String destinationCity,
                      @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String destinationCountry,
                      @NotNull OffsetDateTime startDateTime, @NotNull OffsetDateTime endDateTime,
                      @NotNull TravelRequest.TransportMode transportMode,
                      @NotNull @DecimalMin("0") BigDecimal estimatedCost,
                      @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
                      @NotBlank @Size(max = 32) String costCentre, @Size(max = 32) String projectCode,
                      @Size(max = 1000) String comment, List<@Valid FundingBody> fundings) {
        TravelRequest.Details details() {
            return new TravelRequest.Details(purpose, destinationCity, destinationCountry, startDateTime.toInstant(),
                    endDateTime.toInstant(), transportMode, estimatedCost, currency, costCentre, projectCode, comment);
        }

        List<TravelService.FundingInput> fundingInputs() {
            return fundings == null ? List.of() : fundings.stream()
                    .map(f -> new TravelService.FundingInput(f.fundingSourceId(), f.percentage(), f.amount())).toList();
        }
    }

    record ExpenseBody(@NotNull TravelExpense.Type expenseType, @NotNull LocalDate expenseDate,
                       @NotNull @DecimalMin("0.01") BigDecimal amount, String currency,
                       @Size(max = 500) String description) {
    }

    record DecisionBody(@Size(max = 1000) String comment) {
    }

    record FundingSourceResponse(UUID id, String costCentre, String projectCode, String fundCode, String description) {
        static FundingSourceResponse of(FundingSource f) {
            return new FundingSourceResponse(f.getId(), f.getCostCentre(), f.getProjectCode(), f.getFundCode(),
                    f.getDescription());
        }
    }

    record FundingResponse(FundingSourceResponse source, BigDecimal percentage, BigDecimal amount) {
    }

    record ExpenseResponse(UUID id, TravelExpense.Type expenseType, LocalDate expenseDate, BigDecimal amount,
                           String currency, String description, UUID receiptDocumentId, String receiptFileName,
                           TravelExpense.Status status) {
    }

    record ExportResponse(String type, String status, int attempts, String lastError, Instant processedAt) {
        static ExportResponse of(OutboxEvent e) {
            return new ExportResponse(e.getEventType(), e.getStatus().name(), e.getAttempts(), e.getLastError(),
                    e.getProcessedAt());
        }
    }

    record Actions(boolean edit, boolean submit, boolean cancel, boolean markCompleted, boolean editExpenses,
                   boolean submitExpenses, boolean decide, String decisionStep, UUID taskId) {
    }

    record TravelSummaryResponse(UUID id, String purpose, String destinationCity, String destinationCountry,
                                 Instant startDateTime, Instant endDateTime, BigDecimal estimatedCost,
                                 String currency, TravelStatus status) {
        static TravelSummaryResponse of(TravelRequest r) {
            return new TravelSummaryResponse(r.getId(), r.getPurpose(), r.getDestinationCity(),
                    r.getDestinationCountry(), r.getStartDateTime(), r.getEndDateTime(), r.getEstimatedCost(),
                    r.getCurrency(), r.getStatus());
        }
    }

    record TravelResponse(UUID id, UUID employeeId, String employeeName, String purpose, String destinationCity,
                          String destinationCountry, Instant startDateTime, Instant endDateTime,
                          TravelRequest.TransportMode transportMode, BigDecimal estimatedCost, String currency,
                          String costCentre, String projectCode, String comment, TravelStatus status,
                          List<FundingResponse> fundings, List<ExpenseResponse> expenses, BigDecimal expenseTotal,
                          BigDecimal settledAmount, String externalTravelReference, String settlementReference,
                          String financePostingReference, List<ExportResponse> exports,
                          List<InstanceHistory> history, Actions actions) {
    }

    // ------------------------------------------------------------- endpoints

    @GetMapping("/funding-sources")
    @Operation(summary = "Active funding sources (cost centres, projects)")
    List<FundingSourceResponse> fundingSources() {
        return queries.activeFundingSources().stream().map(FundingSourceResponse::of).toList();
    }

    @GetMapping("/travel")
    @Operation(summary = "The caller's trips, newest first")
    List<TravelSummaryResponse> list() {
        return queries.mine(CurrentUser.require()).stream().map(TravelSummaryResponse::of).toList();
    }

    @GetMapping("/travel/{id}")
    TravelResponse get(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(queries.visible(id, me), me);
    }

    @PostMapping("/travel")
    @Operation(summary = "Create a draft travel request")
    TravelResponse create(@Valid @RequestBody TravelBody body) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(travel.createDraft(me, body.details(), body.fundingInputs()), me);
    }

    @PutMapping("/travel/{id}")
    TravelResponse update(@PathVariable UUID id, @Valid @RequestBody TravelBody body) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(travel.updateDraft(id, me, body.details(), body.fundingInputs()), me);
    }

    @PostMapping("/travel/{id}/submit")
    @Operation(summary = "Validate (incl. cost centre in the finance system) and submit")
    TravelResponse submit(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(travel.submit(id, me), me);
    }

    @PostMapping("/travel/{id}/approve")
    @Operation(summary = "Supervisor approval")
    TravelResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body) {
        return decide(id, Decision.APPROVE, body, "SUPERVISOR_APPROVAL");
    }

    @PostMapping("/travel/{id}/financial-approve")
    @Operation(summary = "Financial approval")
    TravelResponse financialApprove(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body) {
        return decide(id, Decision.APPROVE, body, "FINANCIAL_APPROVAL");
    }

    @PostMapping("/travel/{id}/reject")
    TravelResponse reject(@PathVariable UUID id, @Valid @RequestBody DecisionBody body) {
        return decide(id, Decision.REJECT, body, null);
    }

    @PostMapping("/travel/{id}/return")
    @Operation(summary = "Return the trip (or its expense claim) for correction")
    TravelResponse returnForCorrection(@PathVariable UUID id, @Valid @RequestBody DecisionBody body) {
        return decide(id, Decision.RETURN_FOR_CORRECTION, body, null);
    }

    @PostMapping("/travel/{id}/cancel")
    TravelResponse cancel(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(travel.cancel(id, me), me);
    }

    @PostMapping("/travel/{id}/mark-completed")
    TravelResponse markCompleted(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(travel.markCompleted(id, me), me);
    }

    @GetMapping("/travel/{id}/expenses")
    List<ExpenseResponse> expenses(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        TravelRequest r = queries.visible(id, me);
        return expenseResponses(r);
    }

    @PostMapping("/travel/{id}/expenses")
    TravelResponse addExpense(@PathVariable UUID id, @Valid @RequestBody ExpenseBody body) {
        OpsPrincipal me = CurrentUser.require();
        travel.addExpense(id, me, new TravelService.ExpenseInput(body.expenseType(), body.expenseDate(),
                body.amount(), body.currency(), body.description()));
        return toResponse(queries.visible(id, me), me);
    }

    @DeleteMapping("/travel/{id}/expenses/{expenseId}")
    TravelResponse removeExpense(@PathVariable UUID id, @PathVariable UUID expenseId) {
        OpsPrincipal me = CurrentUser.require();
        travel.removeExpense(id, expenseId, me);
        return toResponse(queries.visible(id, me), me);
    }

    @PostMapping(path = "/travel/{id}/expenses/{expenseId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a receipt (PDF/PNG/JPEG, max 10 MB) to an expense")
    TravelResponse uploadReceipt(@PathVariable UUID id, @PathVariable UUID expenseId,
                                 @RequestPart("file") MultipartFile file) {
        OpsPrincipal me = CurrentUser.require();
        travel.attachReceipt(id, expenseId, file, me);
        return toResponse(queries.visible(id, me), me);
    }

    @GetMapping("/travel/{id}/documents/{documentId}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID id, @PathVariable UUID documentId) {
        queries.visible(id, CurrentUser.require());
        Document doc = documents.find(TravelService.BUSINESS_OBJECT_TYPE, id, documentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(doc.getFileName()).build().toString())
                .body(new InputStreamResource(documents.open(doc)));
    }

    @PostMapping("/travel/{id}/submit-expenses")
    TravelResponse submitExpenses(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(travel.submitExpenses(id, me), me);
    }

    @PostMapping("/travel/{id}/settle")
    @Operation(summary = "Travel office: accept the expense claim and settle (exports settlement and posting)")
    TravelResponse settle(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body) {
        return decide(id, Decision.APPROVE, body, "TRAVEL_OFFICE_REVIEW");
    }

    // ---------------------------------------------------------------- helpers

    private TravelResponse decide(UUID id, Decision decision, DecisionBody body, String step) {
        OpsPrincipal me = CurrentUser.require();
        travel.decide(id, decision, body == null ? null : body.comment(), step, me);
        return toResponse(queries.visible(id, me), me);
    }

    private List<ExpenseResponse> expenseResponses(TravelRequest r) {
        Map<UUID, String> fileNames = documents.documentsOf(TravelService.BUSINESS_OBJECT_TYPE, r.getId()).stream()
                .collect(Collectors.toMap(Document::getId, Document::getFileName));
        return r.getExpenses().stream().map(e -> new ExpenseResponse(e.getId(), e.getExpenseType(),
                e.getExpenseDate(), e.getAmount(), e.getCurrency(), e.getDescription(), e.getReceiptDocumentId(),
                fileNames.get(e.getReceiptDocumentId()), e.getStatus())).toList();
    }

    private TravelResponse toResponse(TravelRequest r, OpsPrincipal me) {
        Map<UUID, FundingSource> sources = queries.fundingSources(
                        r.getFundings().stream().map(TravelFunding::getFundingSourceId).toList()).stream()
                .collect(Collectors.toMap(FundingSource::getId, Function.identity()));
        List<FundingResponse> fundings = r.getFundings().stream().map(f -> new FundingResponse(
                sources.containsKey(f.getFundingSourceId()) ? FundingSourceResponse.of(sources.get(f.getFundingSourceId()))
                        : null, f.getPercentage(), f.getAmount())).toList();
        boolean owner = r.getEmployeeId().equals(me.employeeId());
        TravelStatus s = r.getStatus();
        var task = queries.actionableTask(r, me);
        var actions = new Actions(owner && s == TravelStatus.DRAFT, owner && s == TravelStatus.DRAFT,
                owner && Set.of(TravelStatus.DRAFT, TravelStatus.IN_APPROVAL, TravelStatus.AUTHORIZED).contains(s),
                owner && s == TravelStatus.AUTHORIZED && !r.getStartDateTime().isAfter(Instant.now(clock)),
                owner && s == TravelStatus.COMPLETED, owner && s == TravelStatus.COMPLETED && !r.getExpenses().isEmpty(),
                task.isPresent(), task.map(TaskView::stepType).orElse(null), task.map(TaskView::id).orElse(null));
        String name = persons.displayNames(List.of(r.getEmployeeId())).get(r.getEmployeeId());
        return new TravelResponse(r.getId(), r.getEmployeeId(), name, r.getPurpose(), r.getDestinationCity(),
                r.getDestinationCountry(), r.getStartDateTime(), r.getEndDateTime(), r.getTransportMode(),
                r.getEstimatedCost(), r.getCurrency(), r.getCostCentre(), r.getProjectCode(), r.getComment(), s,
                fundings, expenseResponses(r), r.totalExpenses(), r.getSettledAmount(), r.getExternalTravelReference(),
                r.getSettlementReference(), r.getFinancePostingReference(),
                queries.exports(r).stream().map(ExportResponse::of).toList(), queries.history(r), actions);
    }
}
