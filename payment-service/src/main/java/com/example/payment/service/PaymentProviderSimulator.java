package com.example.payment.service;

import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.ProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stands in for an external payment provider (Stripe, Adyen, ...).
 * <p>
 * Its behaviour is controlled by {@link FailureMode}. The mode is held in memory and is
 * per instance; it resets to {@code NONE} on restart. Demo only.
 */
@Component
public class PaymentProviderSimulator {

    public static final long DEFAULT_DELAY_MS = 5_000;

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderSimulator.class);

    private volatile FailureMode mode = FailureMode.NONE;
    private volatile long delayMs = DEFAULT_DELAY_MS;

    /** Result of a charge attempt that reached the provider. */
    public record ChargeResult(PaymentStatus status, String failureReason) {
    }

    public ChargeResult charge(String operationId, BigDecimal amount) {
        FailureMode current = mode;
        log.info("Charging {} for operation {} (failure mode {})", amount, operationId, current);
        return switch (current) {
            case NONE -> new ChargeResult(PaymentStatus.SUCCESS, null);
            case DECLINE -> new ChargeResult(PaymentStatus.FAILED, "Card declined by provider");
            case ERROR -> throw new ProviderUnavailableException();
            case SLOW -> {
                sleep(delayMs);
                yield new ChargeResult(PaymentStatus.SUCCESS, null);
            }
        };
    }

    public void setMode(FailureMode mode, Long delayMs) {
        this.mode = mode;
        this.delayMs = delayMs == null ? DEFAULT_DELAY_MS : delayMs;
        log.warn("Payment provider failure mode set to {} (delay {} ms)", this.mode, this.delayMs);
    }

    public FailureMode getMode() {
        return mode;
    }

    public long getDelayMs() {
        return delayMs;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
