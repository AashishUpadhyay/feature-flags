package com.featureflags.service;

import com.featureflags.model.FeatureFlag;
import com.featureflags.model.FeatureFlagJob;
import com.featureflags.repository.FeatureFlagJobRepository;
import com.featureflags.repository.FeatureFlagRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeatureFlagService {
    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagJobRepository jobRepository;
    private final FeatureFlagBackgroundService backgroundService;
    private final OrganizationService organizationService;

    @Autowired
    public FeatureFlagService(FeatureFlagRepository featureFlagRepository,
            FeatureFlagJobRepository jobRepository,
            FeatureFlagBackgroundService backgroundService,
            OrganizationService organizationService) {
        this.featureFlagRepository = featureFlagRepository;
        this.jobRepository = jobRepository;
        this.backgroundService = backgroundService;
        this.organizationService = organizationService;
    }

    public boolean getFeatureFlag(Long organizationId, String featureFlagName) {
        return featureFlagRepository
                .findByOrganizationIdAndName(organizationId, featureFlagName)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    /**
     * Set feature flag for a single organization only (immediate, synchronous)
     */
    @Transactional
    public void setFeatureFlag(Long organizationId, String featureFlagName, boolean enabled) {
        FeatureFlag featureFlag = featureFlagRepository
                .findByOrganizationIdAndName(organizationId, featureFlagName)
                .orElseGet(() -> new FeatureFlag(featureFlagName, null, enabled, organizationId));

        featureFlag.setEnabled(enabled);
        featureFlagRepository.save(featureFlag);
    }

    /**
     * Set feature flag for an organization and ALL its descendants (background
     * processing)
     * Returns immediately with a job ID that can be used to track progress
     * 
     * @param organizationId  The parent organization ID
     * @param featureFlagName The feature flag name
     * @param enabled         The flag value
     * @return Job ID for tracking the background process
     */
    @Transactional
    public Long setFeatureFlagWithHierarchy(Long organizationId, String featureFlagName, boolean enabled) {
        // Get count of all organizations that will be affected
        List<Long> descendantIds = organizationService.getAllDescendantIds(organizationId);
        int totalOrganizations = descendantIds.size() + 1; // +1 for parent org

        // Create a job to track the background processing
        FeatureFlagJob job = new FeatureFlagJob(organizationId, featureFlagName, enabled, totalOrganizations);
        job = jobRepository.save(job);

        // Start background processing
        backgroundService.processFeatureFlagHierarchyUpdate(job.getId());

        return job.getId();
    }

    /**
     * Get the status of a background feature flag update job
     */
    public FeatureFlagJob getJobStatus(Long jobId) {
        return backgroundService.getJobStatus(jobId);
    }

    /**
     * Get all background jobs for an organization
     */
    public List<FeatureFlagJob> getJobsForOrganization(Long organizationId) {
        return backgroundService.getJobsForOrganization(organizationId);
    }

    /**
     * Check if an organization has any children (to determine if background
     * processing is needed)
     */
    public boolean organizationHasChildren(Long organizationId) {
        List<Long> descendants = organizationService.getAllDescendantIds(organizationId);
        return !descendants.isEmpty();
    }

    /**
     * Manually retry a failed job
     */
    public Long retryFailedJob(Long jobId) {
        return backgroundService.manualRetryJob(jobId);
    }

    /**
     * Get retry chain for a job
     */
    public List<FeatureFlagJob> getJobRetryChain(Long jobId) {
        return backgroundService.getJobRetryChain(jobId);
    }
}