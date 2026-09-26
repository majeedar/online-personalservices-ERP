package edu.university.ops.absence.api;

import edu.university.ops.absence.api.AbsenceDtos.AbsenceRequestBody;
import edu.university.ops.absence.api.AbsenceDtos.AbsenceResponse;
import edu.university.ops.absence.api.AbsenceDtos.AbsenceSummaryResponse;
import edu.university.ops.absence.api.AbsenceDtos.AllowedActions;
import edu.university.ops.absence.api.AbsenceDtos.DayResponse;
import edu.university.ops.absence.api.AbsenceDtos.DecisionBody;
import edu.university.ops.absence.api.AbsenceDtos.DocumentResponse;
import edu.university.ops.absence.api.AbsenceDtos.LeaveTypeResponse;
import edu.university.ops.absence.api.AbsenceDtos.PersonRef;
import edu.university.ops.absence.api.AbsenceDtos.PreviewResponse;
import edu.university.ops.absence.application.AbsenceQueryService;
import edu.university.ops.absence.application.AbsenceService;
import edu.university.ops.absence.domain.AbsenceRequest;
import edu.university.ops.absence.domain.AbsenceStatus;
import edu.university.ops.absence.domain.LeaveType;
import edu.university.ops.shared.directory.PersonDirectory;
import edu.university.ops.shared.documents.Document;
import edu.university.ops.shared.documents.DocumentService;
import edu.university.ops.shared.exception.BusinessException;
import edu.university.ops.shared.security.CurrentUser;
import edu.university.ops.shared.security.OpsPrincipal;
import edu.university.ops.shared.security.Role;
import edu.university.ops.shared.workflow.WorkflowEnums.Decision;
import edu.university.ops.shared.workflow.WorkflowService;
import edu.university.ops.shared.workflow.WorkflowViews.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Absence API (AGENT.md §35). Controllers stay thin; rules live in the application layer. */
@RestController
@RequestMapping("/api/v1/absences")
@Tag(name = "Absence")
class AbsenceController {

    private final AbsenceService absences;
    private final AbsenceQueryService queries;
    private final WorkflowService workflow;
    private final DocumentService documents;
    private final PersonDirectory persons;

    AbsenceController(AbsenceService absences, AbsenceQueryService queries, WorkflowService workflow,
                      DocumentService documents, PersonDirectory persons) {
        this.absences = absences;
        this.queries = queries;
        this.workflow = workflow;
        this.documents = documents;
        this.persons = persons;
    }

    @GetMapping
    @Operation(summary = "The caller's absence requests, newest first")
    List<AbsenceSummaryResponse> list() {
        Map<UUID, LeaveType> types = queries.leaveTypesById();
        return queries.myRequests(CurrentUser.require()).stream()
                .map(r -> AbsenceSummaryResponse.of(r, types.get(r.getLeaveTypeId()))).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One absence request with days, workflow history and allowed actions")
    AbsenceResponse get(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(queries.visibleRequest(id, me), me);
    }

    @PostMapping("/preview")
    @Operation(summary = "Calculate days and balance and list rule violations, without saving")
    PreviewResponse preview(@Valid @RequestBody AbsenceRequestBody body,
                            @RequestParam(required = false) UUID excludeRequestId) {
        return PreviewResponse.of(absences.preview(CurrentUser.require(), body.toInput(), excludeRequestId));
    }

    @PostMapping
    @Operation(summary = "Create a draft request")
    AbsenceResponse create(@Valid @RequestBody AbsenceRequestBody body) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(absences.createDraft(me, body.toInput()), me);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a draft request")
    AbsenceResponse update(@PathVariable UUID id, @Valid @RequestBody AbsenceRequestBody body) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(absences.updateDraft(id, me, body.toInput()), me);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Validate and submit a draft for approval")
    AbsenceResponse submit(@PathVariable UUID id) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(absences.submit(id, me), me);
    }

    @PostMapping("/{id}/approve")
    AbsenceResponse approve(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body) {
        return decide(id, Decision.APPROVE, body);
    }

    @PostMapping("/{id}/reject")
    AbsenceResponse reject(@PathVariable UUID id, @Valid @RequestBody DecisionBody body) {
        return decide(id, Decision.REJECT, body);
    }

    @PostMapping("/{id}/return")
    AbsenceResponse returnForCorrection(@PathVariable UUID id, @Valid @RequestBody DecisionBody body) {
        return decide(id, Decision.RETURN_FOR_CORRECTION, body);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel: drafts and pending requests directly, approved absences via approval")
    AbsenceResponse cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionBody body) {
        OpsPrincipal me = CurrentUser.require();
        return toResponse(absences.cancel(id, me, body == null ? null : body.comment()), me);
    }

    @PostMapping(path = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a document (PDF/PNG/JPEG, max 10 MB) to one's own request")
    DocumentResponse upload(@PathVariable UUID id, @RequestPart("file") MultipartFile file,
                            @RequestParam(defaultValue = "ATTACHMENT") String documentType) {
        OpsPrincipal me = CurrentUser.require();
        AbsenceRequest request = queries.visibleRequest(id, me);
        if (!request.getEmployeeId().equals(me.employeeId())) {
            throw BusinessException.forbidden();
        }
        return DocumentResponse.of(documents.attach(AbsenceService.BUSINESS_OBJECT_TYPE, id, documentType, file,
                me.employeeId()));
    }

    @GetMapping("/{id}/documents/{documentId}")
    ResponseEntity<InputStreamResource> download(@PathVariable UUID id, @PathVariable UUID documentId) {
        queries.visibleRequest(id, CurrentUser.require());
        Document doc = documents.find(AbsenceService.BUSINESS_OBJECT_TYPE, id, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(doc.getFileName()).build().toString())
                .body(new InputStreamResource(documents.open(doc)));
    }

    private AbsenceResponse decide(UUID id, Decision decision, DecisionBody body) {
        OpsPrincipal me = CurrentUser.require();
        absences.decide(id, decision, body == null ? null : body.comment(), me);
        return toResponse(queries.visibleRequest(id, me), me);
    }

    private AbsenceResponse toResponse(AbsenceRequest r, OpsPrincipal me) {
        LeaveType type = queries.leaveTypesById().get(r.getLeaveTypeId());
        Set<UUID> ids = new HashSet<>();
        ids.add(r.getEmployeeId());
        if (r.getRepresentativeEmployeeId() != null) {
            ids.add(r.getRepresentativeEmployeeId());
        }
        Map<UUID, String> names = persons.displayNames(ids);
        boolean owner = r.getEmployeeId().equals(me.employeeId());
        var task = r.getWorkflowInstanceId() == null ? Optional.<TaskView>empty()
                : workflow.actionableTask(r.getWorkflowInstanceId(), me);
        boolean cancellable = (owner || me.hasRole(Role.HR_ADMIN))
                && Set.of(AbsenceStatus.DRAFT, AbsenceStatus.IN_APPROVAL, AbsenceStatus.APPROVED).contains(r.getStatus());
        var actions = new AllowedActions(owner && r.getStatus() == AbsenceStatus.DRAFT,
                owner && r.getStatus() == AbsenceStatus.DRAFT, cancellable, task.isPresent(),
                task.map(TaskView::id).orElse(null), task.map(t -> t.viaDelegationFrom() != null).orElse(false));
        return new AbsenceResponse(r.getId(), new PersonRef(r.getEmployeeId(), names.get(r.getEmployeeId())),
                LeaveTypeResponse.of(type), r.getStartDate(), r.getEndDate(),
                r.getDayParts().start(), r.getDayParts().end(),
                r.getRepresentativeEmployeeId() == null ? null
                        : new PersonRef(r.getRepresentativeEmployeeId(), names.get(r.getRepresentativeEmployeeId())),
                r.getComment(), r.getStatus(), r.workingDays(), r.totalDeduction(), r.getCreatedAt(),
                r.getSubmittedAt(), r.getDays().stream().map(DayResponse::of).toList(), queries.history(r),
                documents.documentsOf(AbsenceService.BUSINESS_OBJECT_TYPE, r.getId()).stream()
                        .map(DocumentResponse::of).toList(),
                actions, r.getAnonymisedAt());
    }
}
