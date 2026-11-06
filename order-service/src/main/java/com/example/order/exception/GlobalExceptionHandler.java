package com.example.order.exception;

import com.example.order.exception.OrderExceptions.DownstreamServiceException;
import com.example.order.exception.OrderExceptions.IdempotencyKeyMismatchException;
import com.example.order.exception.OrderExceptions.IdempotencyRequestInProgressException;
import com.example.order.exception.OrderExceptions.InsufficientStockException;
import com.example.order.exception.OrderExceptions.OrderNotAwaitingPaymentException;
import com.example.order.exception.OrderExceptions.OrderNotFoundException;
import com.example.order.exception.OrderExceptions.ProductNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RFC 9457 problem+json for every error.
 * <pre>
 * 400 invalid body / missing Idempotency-Key      404 order or product not found
 * 409 out of stock, idempotency conflict, wrong order state
 * 503 downstream unavailable                      504 downstream timed out
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://example.com/problems/";

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "order-not-found", "Order not found", ex.getMessage());
        problem.setProperty("orderId", ex.getOrderId());
        return problem;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "product-not-found", "Product not found", ex.getMessage());
        problem.setProperty("productId", ex.getProductId());
        return problem;
    }

    @ExceptionHandler(InsufficientStockException.class)
    ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "insufficient-stock", "Insufficient stock", ex.getMessage());
        problem.setProperty("productId", ex.getProductId());
        problem.setProperty("requested", ex.getRequested());
        problem.setProperty("available", ex.getAvailable());
        return problem;
    }

    @ExceptionHandler(IdempotencyKeyMismatchException.class)
    ProblemDetail handleKeyMismatch(IdempotencyKeyMismatchException ex) {
        return problem(HttpStatus.CONFLICT, "idempotency-key-mismatch", "Idempotency-Key reused", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyRequestInProgressException.class)
    ProblemDetail handleKeyInProgress(IdempotencyRequestInProgressException ex) {
        return problem(HttpStatus.CONFLICT, "idempotency-in-progress", "Request in progress", ex.getMessage());
    }

    @ExceptionHandler(OrderNotAwaitingPaymentException.class)
    ProblemDetail handleNotAwaitingPayment(OrderNotAwaitingPaymentException ex) {
        return problem(HttpStatus.CONFLICT, "order-not-pending", "Order is not awaiting payment", ex.getMessage());
    }

    @ExceptionHandler(DownstreamServiceException.class)
    ProblemDetail handleDownstream(DownstreamServiceException ex) {
        log.warn("Downstream failure ({}): {}", ex.getService(), ex.getCause() == null ? "" : ex.getCause().toString());
        String type = ex.getStatus() == HttpStatus.GATEWAY_TIMEOUT ? "downstream-timeout" : "downstream-unavailable";
        ProblemDetail problem = problem(ex.getStatus(), type, ex.getStatus().getReasonPhrase(), ex.getMessage());
        problem.setProperty("service", ex.getService());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "concurrent-modification", "Concurrent modification",
                "The order was changed by another request. Reload it and try again.");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleBadSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-sort", "Invalid sort property",
                "Cannot sort by '" + ex.getPropertyName() + "'");
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

    /**
     * When a controller method has constraints on plain parameters (here: the Idempotency-Key header),
     * Spring validates the whole method and reports body and header errors together in this exception.
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(fe -> errors.add(Map.of(
                        "field", fe.getField(),
                        "message", String.valueOf(fe.getDefaultMessage()))));
            } else {
                String name = parameterName(result);
                result.getResolvableErrors().forEach(error -> errors.add(Map.of(
                        "field", name,
                        "message", String.valueOf(error.getDefaultMessage()))));
            }
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed",
                "The request contains invalid values");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static String parameterName(ParameterValidationResult result) {
        RequestHeader header = result.getMethodParameter().getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.value().isEmpty()) {
            return header.value();
        }
        return String.valueOf(result.getMethodParameter().getParameterName());
    }

    private static ProblemDetail problem(HttpStatusCode status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
