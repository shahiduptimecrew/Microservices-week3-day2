package com.example.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Errors produced by the gateway itself (not by a service) as RFC 9457 problem+json,
 * so clients see one error format whether a service or the gateway answered.
 * <pre>
 * no route matches the path            → 404
 * no healthy instance in Eureka        → 503
 * instance refused the connection      → 503
 * instance did not answer in time      → 504
 * </pre>
 * Errors returned by the services pass through unchanged; this handler never sees them.
 */
@Component
@Order(-2) // before Spring Boot's default error handler (-1)
public class GatewayErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        HttpStatusCode status = statusFor(ex);
        String title;
        String detail;
        if (status.value() == 404) {
            title = "Not Found";
            detail = "No route for " + exchange.getRequest().getURI().getRawPath();
        } else if (status.value() == 503) {
            title = "Service Unavailable";
            detail = "The target service is unavailable (no healthy instance, or it refused the connection)";
        } else if (status.value() == 504) {
            title = "Gateway Timeout";
            detail = "The target service did not answer in time";
        } else if (status.is5xxServerError()) {
            title = "Bad Gateway";
            detail = "The gateway could not complete the request";
        } else {
            title = HttpStatus.valueOf(status.value()).getReasonPhrase();
            detail = ex.getMessage();
        }
        log.warn("Gateway error {} for {} {}: {}", status.value(), exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getRawPath(), ex.toString());

        String body = "{"
                + "\"type\":\"https://example.com/problems/gateway-" + status.value() + "\","
                + "\"title\":\"" + json(title) + "\","
                + "\"status\":" + status.value() + ","
                + "\"detail\":\"" + json(detail) + "\","
                + "\"instance\":\"" + json(exchange.getRequest().getURI().getRawPath()) + "\""
                + "}";

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static HttpStatusCode statusFor(Throwable ex) {
        for (Throwable cur = ex; cur != null; cur = cur.getCause()) {
            if (cur instanceof ResponseStatusException rse) {
                return rse.getStatusCode();
            }
            if (cur instanceof TimeoutException || cur instanceof io.netty.handler.timeout.TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (cur instanceof ConnectException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
        }
        return HttpStatus.BAD_GATEWAY;
    }

    private static String json(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}
