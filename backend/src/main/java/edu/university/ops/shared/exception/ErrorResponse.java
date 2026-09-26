package edu.university.ops.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** The single error shape returned by every API endpoint (AGENT.md §33). */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, String correlationId, List<FieldError> details) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String correlationId) {
        return new ErrorResponse(code.name(), message, correlationId, List.of());
    }
}
