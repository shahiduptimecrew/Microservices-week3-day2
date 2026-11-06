package com.example.payment.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

/** RFC 9457 problem+json for every error (same approach as Product Service). */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://example.com/problems/";

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail handleNotFound(PaymentNotFoundException ex) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "payment-not-found", "Payment not found", ex.getMessage());
        problem.setProperty("paymentId", ex.getPaymentId());
        return problem;
    }

    @ExceptionHandler(OperationIdConflictException.class)
    ProblemDetail handleOperationIdConflict(OperationIdConflictException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "operation-id-conflict", "operationId conflict", ex.getMessage());
        problem.setProperty("operationId", ex.getOperationId());
        return problem;
    }

    /** Two identical requests arrived at the same moment; the unique constraint let only one through. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "concurrent-duplicate", "Concurrent duplicate",
                "A payment with this operationId is being processed. Retry to get its result.");
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    ResponseEntity<ProblemDetail> handleProviderDown(ProviderUnavailableException ex) {
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "provider-unavailable",
                "Payment provider unavailable", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "10")
                .body(problem);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal server error",
                "An unexpected error occurred");
    }

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
