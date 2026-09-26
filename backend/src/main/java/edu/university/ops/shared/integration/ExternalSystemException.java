package edu.university.ops.shared.integration;

/**
 * An external system could not be reached or answered with a technical error.
 * Retryable by definition; business rejections are modelled as results instead.
 */
public class ExternalSystemException extends RuntimeException {

    private final ExternalSystem system;
    private final String errorCode;

    public ExternalSystemException(ExternalSystem system, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.system = system;
        this.errorCode = errorCode;
    }

    public ExternalSystemException(ExternalSystem system, String errorCode, String message) {
        this(system, errorCode, message, null);
    }

    public ExternalSystem system() {
        return system;
    }

    public String errorCode() {
        return errorCode;
    }
}
