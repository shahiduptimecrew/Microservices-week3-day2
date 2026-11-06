package com.example.product.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Turns every error into an RFC 9457 {@code application/problem+json} response.
 * <p>
 * The base class {@link ResponseEntityExceptionHandler} already maps Spring MVC's own exceptions
 * (malformed JSON, wrong parameter type, unsupported method, invalid {@code @RequestParam}, ...)
 * to ProblemDetail with the correct 4xx status. This class adds the application's exceptions.
 * <p>
 * Status codes used: 400 (client sent invalid data), 404 (resource does not exist),
 * 409 (valid request that conflicts with current state), 500 (our bug; details are logged, not returned).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://example.com/problems/";

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "product-not-found", "Product not found", ex.getMessage());
        problem.setProperty("productId", ex.getProductId());
        return problem;
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ProblemDetail handleDuplicateSku(DuplicateSkuException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "duplicate-sku", "Duplicate SKU", ex.getMessage());
        problem.setProperty("sku", ex.getSku());
        return problem;
    }

    /** Safety net: two requests created the same SKU at the same moment, and the DB unique constraint caught it. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "data-conflict", "Conflict",
                "The request conflicts with existing data (for example a duplicate SKU)");
    }

    /** Two clients updated the same product concurrently; the later one must reload and retry. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent modification",
                "The product was changed by another request. Reload it and try again.");
    }

    /** {@code ?sort=unknownField} - the client asked for a property that does not exist. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleBadSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-sort", "Invalid sort property",
                "Cannot sort by '" + ex.getPropertyName() + "'");
    }

    /** Last resort: never leak stack traces or internal messages to the client. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred");
    }

    /** {@code @Valid} body failed: return every invalid field, not just the first one. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed",
                "The request body contains invalid fields");
        List<Map<String, Object>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.<String, Object>of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
