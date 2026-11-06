package com.example.order.controller;

import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.OrderStatus;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Adds HATEOAS links to an order (Richardson Maturity Model level 3).
 * <p>
 * The links tell the client what it can do <b>next</b>, based on the order's state:
 * {@code retry-payment} only appears while the order is PENDING. A client that follows links
 * instead of building URLs does not break when URLs change.
 * <p>
 * {@code product} and {@code payment} point to other services. They use the address the client
 * called (via X-Forwarded-* headers), so they are correct when called through the API Gateway.
 */
@Component
public class OrderModelAssembler implements RepresentationModelAssembler<OrderResponse, EntityModel<OrderResponse>> {

    @Override
    public EntityModel<OrderResponse> toModel(OrderResponse order) {
        EntityModel<OrderResponse> model = EntityModel.of(order,
                linkTo(methodOn(OrderController.class).findById(order.id())).withSelfRel(),
                linkTo(OrderController.class).withRel("orders"),
                Link.of(baseUrl() + "/api/v1/products/" + order.productId(), "product"));

        if (order.paymentId() != null) {
            model.add(Link.of(baseUrl() + "/api/v1/payments/" + order.paymentId(), "payment"));
        }
        if (order.status() == OrderStatus.PENDING) {
            model.add(linkTo(methodOn(OrderController.class).retryPayment(order.id())).withRel("retry-payment"));
        }
        return model;
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
    }
}
