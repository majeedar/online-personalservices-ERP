package edu.university.ops.shared.exception;

import edu.university.ops.shared.i18n.Text;

/**
 * A domain or application rule was violated. The message is shown to the user,
 * so it must be meaningful and must not contain sensitive data. It is a
 * {@link Text}, shown in the user's language; {@link #getMessage()} is English (logs).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Text text;

    public BusinessException(ErrorCode code, String message) {
        this(code, Text.of(message));
    }

    public BusinessException(ErrorCode code, Text text) {
        super(text.english());
        this.code = code;
        this.text = text;
    }

    public ErrorCode code() {
        return code;
    }

    public Text text() {
        return text;
    }

    /** @param what the object, e.g. "Absence request" (translated as well) */
    public static BusinessException notFound(ErrorCode code, String what) {
        return new BusinessException(code, Text.of("{what} was not found.", "what", Text.of(what)));
    }

    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.NOT_AUTHORIZED, "You are not authorized to perform this action.");
    }
}
