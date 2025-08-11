package com.featureflags.model;

import java.util.List;

public class OrganizationBulkResult {
    private OperationStatus status;
    private String message;
    private List<Long> orgIds;

    public enum OperationStatus {
        SUCCESS,
        FAILED
    }

    public OrganizationBulkResult() {
    }

    public OrganizationBulkResult(OperationStatus status, String message) {
        this(status, message, null);
    }

    public OrganizationBulkResult(OperationStatus status, String message, List<Long> orgIds) {
        this.status = status;
        this.message = message;
        this.orgIds = orgIds;
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

    public List<Long> getOrgIds() {
        return orgIds;
    }
}