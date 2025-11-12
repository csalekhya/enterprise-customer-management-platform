package io.crmplatform.exception;

/** Thrown when a requested aggregate does not exist. Mapped to HTTP 404 by the advice. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s %s was not found".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
