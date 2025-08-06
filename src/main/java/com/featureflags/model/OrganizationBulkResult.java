package com.featureflags.model;

public class OrganizationBulkResult {
    private OperationStatus status;
    private String message;

    public enum OperationStatus {
        SUCCESS,
        FAILED
    }

    public OrganizationBulkResult() {
    }

    public OrganizationBulkResult(OperationStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public OperationStatus getStatus() {
        return status;
    }

    public void setStatus(OperationStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}