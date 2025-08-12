package com.featureflags.service;

import com.featureflags.model.FeatureFlag;
import com.featureflags.model.FeatureFlagJob;
import com.featureflags.repository.FeatureFlagJobRepository;
import com.featureflags.repository.FeatureFlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeatureFlagBackgroundService {

    private static final Logger logger = LoggerFactory.getLogger(FeatureFlagBackgroundService.class);

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagJobRepository jobRepository;
    private final OrganizationService organizationService;

    @Autowired
    public FeatureFlagBackgroundService(FeatureFlagRepository featureFlagRepository,
            FeatureFlagJobRepository jobRepository,
            OrganizationService organizationService) {
        this.featureFlagRepository = featureFlagRepository;
        this.jobRepository = jobRepository;
        this.organizationService = organizationService;
    }

    /**
     * Process feature flag update for an organization and all its descendants in
     * the background
     * This method returns immediately while the processing happens asynchronously
     */
    @Async("featureFlagTaskExecutor")
    @Transactional
    public void processFeatureFlagHierarchyUpdate(Long jobId) {
        FeatureFlagJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        try {
            logger.info("Starting background processing for job {} - Organization: {}, Flag: {}, Enabled: {}",
                    jobId, job.getOrganizationId(), job.getFeatureFlagName(), job.isEnabled());

            job.markAsInProgress();
            jobRepository.save(job);

            // First, update the parent organization
            updateFeatureFlagForOrganization(job.getOrganizationId(), job.getFeatureFlagName(), job.isEnabled());
            job.incrementProcessedOrganizations();
            jobRepository.save(job);

            // Get all descendant organization IDs
            List<Long> descendantIds = organizationService.getAllDescendantIds(job.getOrganizationId());
            logger.info("Found {} descendant organizations to update for job {}", descendantIds.size(), jobId);

            // Update total count if it wasn't set correctly initially
            if (job.getTotalOrganizations() == null || job.getTotalOrganizations() != descendantIds.size() + 1) {
                job.setTotalOrganizations(descendantIds.size() + 1); // +1 for parent org
                jobRepository.save(job);
            }

            // Process each descendant organization
            for (Long descendantId : descendantIds) {
                try {
                    updateFeatureFlagForOrganization(descendantId, job.getFeatureFlagName(), job.isEnabled());
                    job.incrementProcessedOrganizations();

                    // Save progress every 10 organizations or at the end
                    if (job.getProcessedOrganizations() % 10 == 0 ||
                            job.getProcessedOrganizations().equals(job.getTotalOrganizations())) {
                        jobRepository.save(job);
                        logger.debug("Job {} progress: {}/{} organizations processed",
                                jobId, job.getProcessedOrganizations(), job.getTotalOrganizations());
                    }

                } catch (Exception e) {
                    logger.error("Failed to update feature flag for organization {} in job {}: {}",
                            descendantId, jobId, e.getMessage());
                    // Continue processing other organizations even if one fails
                }
            }

            job.markAsCompleted();
            jobRepository.save(job);

            logger.info("Completed background processing for job {} - Processed {}/{} organizations",
                    jobId, job.getProcessedOrganizations(), job.getTotalOrganizations());

        } catch (Exception e) {
            logger.error("Failed to process job {}: {}", jobId, e.getMessage(), e);

            // Check if job can be retried
            if (job.canRetry()) {
                logger.info("Job {} failed but can be retried. Retry count: {}/{}",
                        jobId, job.getRetryCount(), job.getMaxRetries());
                scheduleRetry(job);
            } else {
                logger.error("Job {} failed and exhausted all retries. Marking as permanently failed.", jobId);
                job.markAsFailed(e.getMessage());
                jobRepository.save(job);
            }
        }
    }

    /**
     * Update feature flag for a single organization (denormalized storage)
     */
    private void updateFeatureFlagForOrganization(Long organizationId, String featureFlagName, boolean enabled) {
        FeatureFlag featureFlag = featureFlagRepository
                .findByOrganizationIdAndName(organizationId, featureFlagName)
                .orElseGet(() -> new FeatureFlag(featureFlagName, null, enabled, organizationId));

        featureFlag.setEnabled(enabled);
        featureFlagRepository.save(featureFlag);
    }

    /**
     * Get job status for tracking background processing
     */
    public FeatureFlagJob getJobStatus(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
    }

    /**
     * Get all jobs for an organization
     */
    public List<FeatureFlagJob> getJobsForOrganization(Long organizationId) {
        return jobRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    /**
     * Schedule a retry for a failed job with exponential backoff
     */
    private void scheduleRetry(FeatureFlagJob failedJob) {
        failedJob.incrementRetryCount();
        failedJob.markAsRetrying();
        jobRepository.save(failedJob);

        // Create a new retry job
        FeatureFlagJob retryJob = new FeatureFlagJob(
                failedJob.getOrganizationId(),
                failedJob.getFeatureFlagName(),
                failedJob.isEnabled(),
                failedJob.getTotalOrganizations(),
                failedJob.getId() // Set parent job ID
        );
        retryJob.setRetryCount(failedJob.getRetryCount());
        retryJob.setMaxRetries(failedJob.getMaxRetries());
        retryJob = jobRepository.save(retryJob);

        // Calculate delay with exponential backoff: 2^retryCount seconds
        long delaySeconds = (long) Math.pow(2, failedJob.getRetryCount());

        logger.info("Scheduling retry for job {} in {} seconds. New retry job ID: {}",
                failedJob.getId(), delaySeconds, retryJob.getId());

        // Schedule the retry asynchronously with delay
        scheduleDelayedRetry(retryJob.getId(), delaySeconds);
    }

    /**
     * Schedule a delayed retry execution
     */
    @Async("featureFlagTaskExecutor")
    public void scheduleDelayedRetry(Long retryJobId, long delaySeconds) {
        try {
            // Wait for the delay period
            Thread.sleep(delaySeconds * 1000);

            logger.info("Starting delayed retry for job {}", retryJobId);
            processFeatureFlagHierarchyUpdate(retryJobId);

        } catch (InterruptedException e) {
            logger.warn("Retry scheduling interrupted for job {}", retryJobId);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Manually retry a failed job (for admin/operator use)
     */
    public Long manualRetryJob(Long jobId) {
        FeatureFlagJob originalJob = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (originalJob.getStatus() != FeatureFlagJob.JobStatus.FAILED) {
            throw new IllegalStateException("Can only retry failed jobs. Current status: " + originalJob.getStatus());
        }

        // Create a new retry job
        FeatureFlagJob retryJob = new FeatureFlagJob(
                originalJob.getOrganizationId(),
                originalJob.getFeatureFlagName(),
                originalJob.isEnabled(),
                originalJob.getTotalOrganizations(),
                originalJob.getId());
        retryJob.setMaxRetries(originalJob.getMaxRetries() + 1); // Allow one more retry for manual retries
        retryJob = jobRepository.save(retryJob);

        logger.info("Manual retry initiated for job {}. New retry job ID: {}", jobId, retryJob.getId());

        // Start processing immediately
        processFeatureFlagHierarchyUpdate(retryJob.getId());

        return retryJob.getId();
    }

    /**
     * Get retry chain for a job (original job + all retries)
     */
    public List<FeatureFlagJob> getJobRetryChain(Long jobId) {
        FeatureFlagJob job = getJobStatus(jobId);

        // Find the root job (original job)
        Long rootJobId = job.getParentJobId() != null ? job.getParentJobId() : job.getId();

        // Get all jobs in the retry chain
        List<FeatureFlagJob> allJobs = jobRepository.findByOrganizationIdOrderByCreatedAtDesc(job.getOrganizationId());

        return allJobs.stream()
                .filter(j -> j.getId().equals(rootJobId) || rootJobId.equals(j.getParentJobId()))
                .collect(java.util.stream.Collectors.toList());
    }
}
