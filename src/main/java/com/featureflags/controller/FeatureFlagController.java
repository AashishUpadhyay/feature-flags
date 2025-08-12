package com.featureflags.controller;

import com.featureflags.model.FeatureFlag;
import com.featureflags.model.FeatureFlagJob;
import com.featureflags.service.FeatureFlagService;
import com.featureflags.service.FeatureFlagValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;
    private final FeatureFlagValidator featureFlagValidator;

    @Autowired
    public FeatureFlagController(FeatureFlagService featureFlagService, FeatureFlagValidator featureFlagValidator) {
        this.featureFlagService = featureFlagService;
        this.featureFlagValidator = featureFlagValidator;
    }

    @GetMapping("/{orgId}/{featureFlagName}")
    public ResponseEntity<FeatureFlag> getFeatureFlag(
            @PathVariable Long orgId,
            @PathVariable String featureFlagName) {
        if (!featureFlagValidator.isFeatureFlagRegistered(featureFlagName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Feature flag '" + featureFlagName + "' is not registered");
        }
        boolean enabled = featureFlagService.getFeatureFlag(orgId, featureFlagName);
        FeatureFlag featureFlag = new FeatureFlag(featureFlagName, null, enabled, orgId);
        return ResponseEntity.ok(featureFlag);
    }

    /**
     * Set feature flag for an organization
     * Automatically determines whether to process immediately or in background
     * based on hierarchy
     * POST /{orgId}/{featureFlagName}/{enabled}
     */
    @PostMapping("/{orgId}/{featureFlagName}/{enabled}")
    public ResponseEntity<Map<String, Object>> setFeatureFlag(
            @PathVariable Long orgId,
            @PathVariable String featureFlagName,
            @PathVariable boolean enabled,
            @RequestParam(required = false) String description) {

        if (!featureFlagValidator.isFeatureFlagRegistered(featureFlagName)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Feature flag '" + featureFlagName + "' is not registered");
        }

        // Check if organization has children to determine processing strategy
        boolean hasChildren = featureFlagService.organizationHasChildren(orgId);

        Map<String, Object> response = new HashMap<>();

        if (!hasChildren) {
            // No children, process immediately (synchronous)
            featureFlagService.setFeatureFlag(orgId, featureFlagName, enabled);

            // Return immediate response with feature flag details
            FeatureFlag featureFlag = new FeatureFlag(featureFlagName, description, enabled, orgId);
            response.put("featureFlag", featureFlag);
            response.put("message", "Feature flag updated immediately");
            response.put("processedImmediately", true);
            response.put("jobId", null);

            return ResponseEntity.ok(response);

        } else {
            // Has children, process in background (asynchronous)
            Long jobId = featureFlagService.setFeatureFlagWithHierarchy(orgId, featureFlagName, enabled);

            response.put("message", "Background processing started for organization hierarchy");
            response.put("processedImmediately", false);
            response.put("jobId", jobId);
            response.put("trackingUrl", "/job-status/" + jobId);
            response.put("organizationId", orgId);
            response.put("featureFlagName", featureFlagName);
            response.put("enabled", enabled);

            return ResponseEntity.accepted().body(response);
        }
    }

    /**
     * Get status of background job
     * GET /job-status/{jobId}
     */
    @GetMapping("/job-status/{jobId}")
    public ResponseEntity<FeatureFlagJob> getJobStatus(@PathVariable Long jobId) {
        try {
            FeatureFlagJob job = featureFlagService.getJobStatus(jobId);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        }
    }

    /**
     * Get all jobs for an organization
     * GET /jobs/{orgId}
     */
    @GetMapping("/jobs/{orgId}")
    public ResponseEntity<List<FeatureFlagJob>> getJobsForOrganization(@PathVariable Long orgId) {
        List<FeatureFlagJob> jobs = featureFlagService.getJobsForOrganization(orgId);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Manually retry a failed job
     * POST /retry-job/{jobId}
     */
    @PostMapping("/retry-job/{jobId}")
    public ResponseEntity<Map<String, Object>> retryFailedJob(@PathVariable Long jobId) {
        try {
            Long retryJobId = featureFlagService.retryFailedJob(jobId);

            Map<String, Object> response = new HashMap<>();
            response.put("originalJobId", jobId);
            response.put("retryJobId", retryJobId);
            response.put("message", "Job retry initiated successfully");
            response.put("trackingUrl", "/job-status/" + retryJobId);

            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Get retry chain for a job (original + all retries)
     * GET /job-retry-chain/{jobId}
     */
    @GetMapping("/job-retry-chain/{jobId}")
    public ResponseEntity<List<FeatureFlagJob>> getJobRetryChain(@PathVariable Long jobId) {
        try {
            List<FeatureFlagJob> retryChain = featureFlagService.getJobRetryChain(jobId);
            return ResponseEntity.ok(retryChain);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
        }
    }
}