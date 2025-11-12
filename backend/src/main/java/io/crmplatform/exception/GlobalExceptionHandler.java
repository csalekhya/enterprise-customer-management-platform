package io.crmplatform.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception that escapes the service layer into an RFC 7807 {@code ProblemDetail}.
 *
 * <p>A single, uniform error contract matters more than it looks: the React client has exactly one
 * error parser, and the {@code type} URI gives the front end a stable machine-readable
 * discriminator that does not depend on prose.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://docs.slatecrm.io/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more fields are invalid. See the errors member for details.",
                "validation-error",
                request);
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail onConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(v -> violations.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more request parameters are invalid.",
                "validation-error",
                request);
        problem.setProperty("errors", violations);
        return problem;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail onMalformedRequest(Exception ex, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Malformed request",
                "The request could not be parsed: " + rootMessage(ex),
                "malformed-request",
                request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail onNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "not-found", request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail onDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), "duplicate-resource", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), rootMessage(ex));
        return problem(
                HttpStatus.CONFLICT,
                "Conflict",
                "The request violates a data integrity rule. A related record may already exist or still be referenced.",
                "data-integrity",
                request);
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail onForbiddenOperation(ForbiddenOperationException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), "ownership-required", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "Your role does not permit this operation.",
                "insufficient-role",
                request);
    }

    @ExceptionHandler(DisabledException.class)
    public ProblemDetail onDisabled(DisabledException ex, HttpServletRequest request) {
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Account disabled",
                "This account has been disabled. Contact an administrator.",
                "account-disabled",
                request);
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ProblemDetail onAuthenticationFailure(AuthenticationException ex, HttpServletRequest request) {
        // Deliberately vague: distinguishing "unknown email" from "wrong password" is a
        // user-enumeration oracle.
        return problem(
                HttpStatus.UNAUTHORIZED,
                "Authentication failed",
                "Invalid credentials.",
                "invalid-credentials",
                request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "The request could not be completed. The incident has been logged.",
                "internal-error",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status, String title, String detail, String slug, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(PROBLEM_BASE + slug));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }
}
