package com.lms.certificate.common.exception;

/**
 * Indicates that a required LMS internal API call could not produce a usable
 * response. This avoids leaking a null body into certificate workflows.
 */
public class LmsBackendUnavailableException extends RuntimeException {

    public LmsBackendUnavailableException(String message) {
        super(message);
    }

    public LmsBackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
