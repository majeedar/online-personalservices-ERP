package edu.university.ops.shared.exception;

/**
 * A domain or application rule was violated. The message is shown to the user,
 * so it must be meaningful and must not contain sensitive data.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static BusinessException notFound(ErrorCode code, String what) {
        return new BusinessException(code, what + " was not found.");
    }

    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.NOT_AUTHORIZED, "You are not authorized to perform this action.");
    }
}
