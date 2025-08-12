package com.featureflags.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feature_flag_jobs")
public class FeatureFlagJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "feature_flag_name", nullable = false)
    private String featureFlagName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Column(name = "total_organizations")
    private Integer totalOrganizations;

    @Column(name = "processed_organizations")
    private Integer processedOrganizations;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "parent_job_id")
    private Long parentJobId;

    public enum JobStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        RETRYING
    }

    public FeatureFlagJob() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = JobStatus.PENDING;
        this.processedOrganizations = 0;
        this.retryCount = 0;
        this.maxRetries = 3; // Default max retries
    }

    public FeatureFlagJob(Long organizationId, String featureFlagName, boolean enabled, Integer totalOrganizations) {
        this();
        this.organizationId = organizationId;
        this.featureFlagName = featureFlagName;
        this.enabled = enabled;
        this.totalOrganizations = totalOrganizations;
    }

    public FeatureFlagJob(Long organizationId, String featureFlagName, boolean enabled, Integer totalOrganizations,
            Long parentJobId) {
        this(organizationId, featureFlagName, enabled, totalOrganizations);
        this.parentJobId = parentJobId;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId) {
        this.organizationId = organizationId;
    }

    public String getFeatureFlagName() {
        return featureFlagName;
    }

    public void setFeatureFlagName(String featureFlagName) {
        this.featureFlagName = featureFlagName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public Integer getTotalOrganizations() {
        return totalOrganizations;
    }

    public void setTotalOrganizations(Integer totalOrganizations) {
        this.totalOrganizations = totalOrganizations;
    }

    public Integer getProcessedOrganizations() {
        return processedOrganizations;
    }

    public void setProcessedOrganizations(Integer processedOrganizations) {
        this.processedOrganizations = processedOrganizations;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markAsInProgress() {
        this.status = JobStatus.IN_PROGRESS;
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        this.status = JobStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAsFailed(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementProcessedOrganizations() {
        this.processedOrganizations++;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Long getParentJobId() {
        return parentJobId;
    }

    public void setParentJobId(Long parentJobId) {
        this.parentJobId = parentJobId;
    }

    public void incrementRetryCount() {
        this.retryCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    public void markAsRetrying() {
        this.status = JobStatus.RETRYING;
        this.updatedAt = LocalDateTime.now();
    }

    public void resetForRetry() {
        this.processedOrganizations = 0;
        this.errorMessage = null;
        this.completedAt = null;
        this.status = JobStatus.PENDING;
        this.updatedAt = LocalDateTime.now();
    }
}
