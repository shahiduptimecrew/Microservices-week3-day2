package com.example.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;

/**
 * Runs for every routed request.
 * <ul>
 *   <li>Gives each request a correlation id ({@code X-Request-Id}): taken from the client if present,
 *       otherwise generated. It is forwarded to the service and returned to the client, so one id
 *       can be searched across the logs of all services.</li>
 *   <li>Adds {@code X-Served-By} (the instance host:port the load balancer picked) to the response.
 *       This makes load balancing visible in Postman: call {@code GET /api/v1/products/1} a few
 *       times and watch the port alternate between 8081 and 8084.</li>
 *   <li>Logs one line per request: method, path, chosen instance, status and duration.</li>
 * </ul>
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String SERVED_BY = "X-Served-By";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.nanoTime();
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        String finalRequestId = requestId;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(REQUEST_ID, finalRequestId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();

        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(REQUEST_ID, finalRequestId);
            String target = targetInstance(mutated);
            if (target != null) {
                mutated.getResponse().getHeaders().set(SERVED_BY, target);
            }
            return Mono.empty();
        });

        return chain.filter(mutated).doFinally(signal -> log.info("{} {} {} -> {} status={} {}ms",
                finalRequestId,
                request.getMethod(),
                request.getURI().getRawPath(),
                targetInstance(mutated),
                mutated.getResponse().getStatusCode(),
                (System.nanoTime() - start) / 1_000_000));
    }

    /** host:port of the instance chosen by the load balancer (set by the gateway once it has resolved lb://). */
    private static String targetInstance(ServerWebExchange exchange) {
        URI url = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
        if (url == null || url.getHost() == null || "lb".equals(url.getScheme())) {
            return null;
        }
        return url.getHost() + ":" + url.getPort();
    }

    /** Run first, so the request id exists for everything that follows. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
