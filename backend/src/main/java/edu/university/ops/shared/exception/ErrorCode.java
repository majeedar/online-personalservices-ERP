package edu.university.ops.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to API clients. The frontend
 * may branch on these; never rename one without a migration plan.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    NOT_AUTHORIZED(HttpStatus.FORBIDDEN),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND),
    WORK_SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND),

    INVALID_WORKFLOW_STATE(HttpStatus.CONFLICT),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),

    // Absence rules (AGENT.md §13.7)
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    ABSENCE_OVERLAP(HttpStatus.CONFLICT),
    INSUFFICIENT_LEAVE_BALANCE(HttpStatus.UNPROCESSABLE_ENTITY),
    NO_LEAVE_ENTITLEMENT(HttpStatus.UNPROCESSABLE_ENTITY),
    ABSENCE_NO_WORKING_DAYS(HttpStatus.UNPROCESSABLE_ENTITY),
    LEAVE_TYPE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    EMPLOYEE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    EMPLOYMENT_NOT_COVERING(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_REPRESENTATIVE(HttpStatus.UNPROCESSABLE_ENTITY),
    ATTACHMENT_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),

    // Time rules (AGENT.md §15)
    TIME_SEQUENCE_INVALID(HttpStatus.CONFLICT),
    TIME_MONTH_CLOSED(HttpStatus.CONFLICT),
    TIME_MONTH_NOT_CLOSABLE(HttpStatus.UNPROCESSABLE_ENTITY),

    // Travel rules (AGENT.md §14)
    COST_CENTRE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    FUNDING_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    DOCUMENT_INVALID(HttpStatus.BAD_REQUEST),

    EXTERNAL_SYSTEM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
