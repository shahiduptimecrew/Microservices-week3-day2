package com.example.payment.dto.response;

import com.example.payment.service.FailureMode;

public record FailureModeResponse(FailureMode mode, long delayMs) {
}
