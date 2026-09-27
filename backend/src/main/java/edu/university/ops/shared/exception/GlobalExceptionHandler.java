package edu.university.ops.shared.exception;

import edu.university.ops.shared.i18n.Text;
import edu.university.ops.shared.integration.ExternalSystemException;
import edu.university.ops.shared.monitoring.CorrelationId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps exceptions to {@link ErrorResponse}. Stack traces are logged, never returned.
 * Messages are rendered in the request's language (ADR-020); logs stay English.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> business(BusinessException ex) {
        log.info("Business rule violated: code={} message={}", ex.code(), ex.getMessage());
        return respond(ex.code(), ex.text());
    }

    @ExceptionHandler(ExternalSystemException.class)
    ResponseEntity<ErrorResponse> external(ExternalSystemException ex) {
        log.warn("External system unavailable: system={} code={}", ex.system(), ex.errorCode());
        return respond(ErrorCode.EXTERNAL_SYSTEM_UNAVAILABLE,
                Text.of("A connected university system is currently unavailable. Please try again later."));
    }

    /** Field messages come from Bean Validation, which already uses the request's locale. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        var body = new ErrorResponse(ErrorCode.VALIDATION_FAILED.name(),
                Text.of("The request contains invalid fields.").render(), CorrelationId.current(), details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    ResponseEntity<ErrorResponse> unreadable(Exception ex) {
        return respond(ErrorCode.VALIDATION_FAILED, Text.of("The request could not be read."));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> concurrent(ObjectOptimisticLockingFailureException ex) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION,
                Text.of("The record was changed by someone else. Reload and try again."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> denied(AccessDeniedException ex) {
        return respond(ErrorCode.NOT_AUTHORIZED, Text.of("You are not authorized to perform this action."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> noResource(NoResourceFoundException ex) {
        return respond(ErrorCode.RESOURCE_NOT_FOUND, Text.of("The requested resource does not exist."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return respond(ErrorCode.INTERNAL_ERROR,
                Text.of("An unexpected error occurred. Please contact support with the correlation ID."));
    }

    private static ResponseEntity<ErrorResponse> respond(ErrorCode code, Text message) {
        return ResponseEntity.status(code.status())
                .body(ErrorResponse.of(code, message.render(), CorrelationId.current()));
    }
}
