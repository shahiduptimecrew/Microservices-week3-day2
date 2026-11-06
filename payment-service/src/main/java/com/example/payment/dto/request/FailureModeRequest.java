package com.example.payment.dto.request;

import com.example.payment.service.FailureMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Demo switch for the simulated payment provider.
 *
 * @param mode    NONE, ERROR, SLOW or DECLINE
 * @param delayMs only for SLOW: how long each payment takes (default 5000 ms)
 */
public record FailureModeRequest(
        @NotNull FailureMode mode,
        @Min(0) @Max(30_000) Long delayMs
) {
}
