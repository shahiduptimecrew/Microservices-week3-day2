package com.example.payment.exception;

/** The operationId was already used for a different order, amount or currency. Mapped to 409. */
public class OperationIdConflictException extends RuntimeException {

    private final String operationId;

    public OperationIdConflictException(String operationId) {
        super("operationId '" + operationId + "' was already used with a different order, amount or currency");
        this.operationId = operationId;
    }

    public String getOperationId() {
        return operationId;
    }
}
