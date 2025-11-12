package io.crmplatform.exception;

/** Thrown when a uniqueness rule (e.g. user email) would be violated. Mapped to HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
