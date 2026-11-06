package com.example.payment.exception;

/** The (simulated) external payment provider is down. Mapped to 503; nothing is stored. */
public class ProviderUnavailableException extends RuntimeException {

    public ProviderUnavailableException() {
        super("The payment provider is temporarily unavailable");
    }
}
