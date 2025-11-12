package io.crmplatform.exception;

/**
 * Thrown when the caller is authenticated and holds the right role, but the specific record is
 * outside their scope - for example a SALES_REP editing a contact they do not own.
 *
 * <p>Kept distinct from Spring Security's {@code AccessDeniedException} so that annotation-level
 * denials (wrong role) and data-level denials (wrong owner) can be told apart in logs.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
