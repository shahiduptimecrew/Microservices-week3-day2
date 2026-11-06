package com.example.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An order, stored in orderdb.
 * <p>
 * Only the product <b>id</b> is stored, not a foreign key: productdb belongs to another service.
 * The unit price is copied at order time, so later price changes do not alter existing orders.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    private Long paymentId;

    /**
     * Idempotency key for Payment Service, generated once when the order is created and never changed.
     * Every payment attempt for this order (first try and every retry) sends the same value, so Payment
     * Service can charge at most once. A UUID rather than the order id: ids restart at 1 when the
     * in-memory orderdb is recreated, and "order-1" must never match a payment of an older order.
     */
    @Column(nullable = false, unique = true, length = 50, updatable = false)
    private String paymentOperationId;

    @Column(length = 300)
    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {
        // required by JPA
    }

    public Order(Long productId, int quantity, BigDecimal unitPrice, String currency) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.currency = currency;
        this.status = OrderStatus.CREATED;
        this.paymentStatus = PaymentStatus.NOT_INITIATED;
        this.paymentOperationId = "order-" + UUID.randomUUID();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markPaid(Long paymentId) {
        this.status = OrderStatus.CONFIRMED;
        this.paymentStatus = PaymentStatus.SUCCESS;
        this.paymentId = paymentId;
        this.failureReason = null;
    }

    public void markPaymentFailed(Long paymentId, String reason) {
        this.status = OrderStatus.FAILED;
        this.paymentStatus = PaymentStatus.FAILED;
        this.paymentId = paymentId;
        this.failureReason = reason;
    }

    public void markPaymentPending(String reason) {
        this.status = OrderStatus.PENDING;
        this.paymentStatus = PaymentStatus.PENDING;
        this.failureReason = reason;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public OrderStatus getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public Long getPaymentId() { return paymentId; }
    public String getPaymentOperationId() { return paymentOperationId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
