package com.example.order.controller;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.service.OrderService;
import com.example.order.service.OrderService.CreateOrderResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <pre>
 * POST /api/v1/orders                      201 Created + Location (header Idempotency-Key required)
 * GET  /api/v1/orders?page=&size=&sort=    200 OK, HAL page with links
 * GET  /api/v1/orders/{id}                 200 OK | 404
 * POST /api/v1/orders/{id}/retry-payment   200 OK | 409 if the order is not PENDING
 * </pre>
 * Responses are HAL ({@code application/hal+json}) with {@code _links}.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final OrderService orderService;
    private final OrderModelAssembler assembler;
    private final PagedResourcesAssembler<OrderResponse> pagedAssembler;

    public OrderController(OrderService orderService, OrderModelAssembler assembler,
                           PagedResourcesAssembler<OrderResponse> pagedAssembler) {
        this.orderService = orderService;
        this.assembler = assembler;
        this.pagedAssembler = pagedAssembler;
    }

    /**
     * POST is not idempotent by nature. The {@code Idempotency-Key} header makes it safe to retry:
     * the same key and body return the original order (same status, {@code Idempotent-Replayed: true})
     * instead of creating a second one.
     */
    @PostMapping
    public ResponseEntity<EntityModel<OrderResponse>> create(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResult result = orderService.create(idempotencyKey, request);
        EntityModel<OrderResponse> model = assembler.toModel(result.order());
        return ResponseEntity.created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .header(IDEMPOTENT_REPLAYED, String.valueOf(result.replayed()))
                .body(model);
    }

    @GetMapping
    public PagedModel<EntityModel<OrderResponse>> findAll(
            @ParameterObject
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return pagedAssembler.toModel(orderService.findAll(pageable), assembler);
    }

    @GetMapping("/{id}")
    public EntityModel<OrderResponse> findById(@PathVariable Long id) {
        return assembler.toModel(orderService.findById(id));
    }

    /** Action resource: re-sends the payment for a PENDING order (same operationId, so never a double charge). */
    @PostMapping("/{id}/retry-payment")
    public EntityModel<OrderResponse> retryPayment(@PathVariable Long id) {
        return assembler.toModel(orderService.retryPayment(id));
    }
}
