package com.gatekeeper.common;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 problem-details responses for every error path. Extends
 * ResponseEntityExceptionHandler so Spring MVC's own exceptions (validation, malformed body,
 * unsupported method, etc.) already come out as ProblemDetail — this class adds the
 * project-specific ones on top. Never leaks stack traces or SQL. See doc/scope.md §9.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        var responseBuilder = ResponseEntity.status(ex.getStatus());
        if (ex instanceof com.gatekeeper.security.RateLimitExceededException rateLimitEx) {
            responseBuilder.header(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimitEx.getRetryAfterSeconds()));
        }
        return responseBuilder.body(body);
    }

    /** Concurrent flag updates -> 409, not 500. See doc/scope.md §7.3, §10 T18. */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The resource was modified concurrently");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** @PreAuthorize denials thrown from service methods -> 403. See doc/scope.md §10 T5. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Insufficient permissions");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Last resort — never echo the raw exception message (could contain SQL, stack details, etc.) to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                               HttpStatusCode statusCode, WebRequest request) {
        if (body == null) {
            body = ProblemDetail.forStatusAndDetail(statusCode, ex.getMessage());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}
