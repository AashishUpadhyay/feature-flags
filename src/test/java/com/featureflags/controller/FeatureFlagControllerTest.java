package com.featureflags.controller;

import com.featureflags.model.FeatureFlag;
import com.featureflags.model.FeatureFlagJob;
import com.featureflags.service.FeatureFlagService;
import com.featureflags.service.FeatureFlagValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagControllerTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private FeatureFlagValidator featureFlagValidator;

    @InjectMocks
    private FeatureFlagController featureFlagController;

    private static final Long ORG_ID = 1L;
    private static final String FEATURE_FLAG_NAME = "test-feature";

    @BeforeEach
    void setUp() {
        // Any common setup can go here
    }

    @Test
    void getFeatureFlag_WhenEnabled_ReturnsEnabledFeatureFlag() {
        // Given
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(true);
        when(featureFlagService.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME)).thenReturn(true);

        // When
        ResponseEntity<FeatureFlag> response = featureFlagController.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);

        // Then
        assertTrue(response.getBody().isEnabled());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getName());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verify(featureFlagService).getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);
    }

    @Test
    void getFeatureFlag_WhenDisabled_ReturnsDisabledFeatureFlag() {
        // Given
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(true);
        when(featureFlagService.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME)).thenReturn(false);

        // When
        ResponseEntity<FeatureFlag> response = featureFlagController.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);

        // Then
        assertFalse(response.getBody().isEnabled());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getName());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verify(featureFlagService).getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME);
    }

    @Test
    void setFeatureFlag_NoChildren_ProcessesImmediately() {
        // Given
        boolean enabled = true;
        String description = "Test description";
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(true);
        when(featureFlagService.organizationHasChildren(ORG_ID)).thenReturn(false);
        doNothing().when(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);

        // When
        ResponseEntity<Map<String, Object>> response = featureFlagController.setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME,
                enabled, description);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> responseBody = response.getBody();
        assertTrue((Boolean) responseBody.get("processedImmediately"));
        assertNull(responseBody.get("jobId"));
        assertEquals("Feature flag updated immediately", responseBody.get("message"));

        // Check feature flag in response
        FeatureFlag featureFlag = (FeatureFlag) responseBody.get("featureFlag");
        assertNotNull(featureFlag);
        assertEquals(FEATURE_FLAG_NAME, featureFlag.getName());
        assertEquals(ORG_ID, featureFlag.getOrganizationId());
        assertTrue(featureFlag.isEnabled());
        assertEquals(description, featureFlag.getDescription());

        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verify(featureFlagService).organizationHasChildren(ORG_ID);
        verify(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);
    }

    @Test
    void setFeatureFlag_HasChildren_ProcessesInBackground() {
        // Given
        boolean enabled = true;
        Long jobId = 123L;
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(true);
        when(featureFlagService.organizationHasChildren(ORG_ID)).thenReturn(true);
        when(featureFlagService.setFeatureFlagWithHierarchy(ORG_ID, FEATURE_FLAG_NAME, enabled)).thenReturn(jobId);

        // When
        ResponseEntity<Map<String, Object>> response = featureFlagController.setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME,
                enabled, null);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        Map<String, Object> responseBody = response.getBody();
        assertFalse((Boolean) responseBody.get("processedImmediately"));
        assertEquals(jobId, responseBody.get("jobId"));
        assertEquals("Background processing started for organization hierarchy", responseBody.get("message"));
        assertEquals("/job-status/" + jobId, responseBody.get("trackingUrl"));
        assertEquals(ORG_ID, responseBody.get("organizationId"));
        assertEquals(FEATURE_FLAG_NAME, responseBody.get("featureFlagName"));
        assertEquals(enabled, responseBody.get("enabled"));

        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verify(featureFlagService).organizationHasChildren(ORG_ID);
        verify(featureFlagService).setFeatureFlagWithHierarchy(ORG_ID, FEATURE_FLAG_NAME, enabled);
        verify(featureFlagService, never()).setFeatureFlag(any(), any(), anyBoolean());
    }

    @Test
    void setFeatureFlag_WithoutDescription_NoChildren_ProcessesImmediately() {
        // Given
        boolean enabled = false;
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(true);
        when(featureFlagService.organizationHasChildren(ORG_ID)).thenReturn(false);
        doNothing().when(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);

        // When
        ResponseEntity<Map<String, Object>> response = featureFlagController.setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME,
                enabled, null);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Map<String, Object> responseBody = response.getBody();
        assertTrue((Boolean) responseBody.get("processedImmediately"));

        // Check feature flag in response
        FeatureFlag featureFlag = (FeatureFlag) responseBody.get("featureFlag");
        assertNotNull(featureFlag);
        assertEquals(FEATURE_FLAG_NAME, featureFlag.getName());
        assertEquals(ORG_ID, featureFlag.getOrganizationId());
        assertFalse(featureFlag.isEnabled());
        assertNull(featureFlag.getDescription());

        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verify(featureFlagService).organizationHasChildren(ORG_ID);
        verify(featureFlagService).setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled);
    }

    @Test
    void getFeatureFlag_WhenNotRegistered_ThrowsNotFoundException() {
        // Given
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(false);

        // When & Then
        assertThrows(ResponseStatusException.class,
                () -> featureFlagController.getFeatureFlag(ORG_ID, FEATURE_FLAG_NAME));

        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void setFeatureFlag_WhenNotRegistered_ThrowsNotFoundException() {
        // Given
        boolean enabled = true;
        when(featureFlagValidator.isFeatureFlagRegistered(FEATURE_FLAG_NAME)).thenReturn(false);

        // When & Then
        assertThrows(ResponseStatusException.class,
                () -> featureFlagController.setFeatureFlag(ORG_ID, FEATURE_FLAG_NAME, enabled, null));

        verify(featureFlagValidator).isFeatureFlagRegistered(FEATURE_FLAG_NAME);
        verifyNoInteractions(featureFlagService);
    }

    @Test
    void getJobStatus_ValidJobId_ReturnsJobStatus() {
        // Given
        Long jobId = 123L;
        FeatureFlagJob job = new FeatureFlagJob(ORG_ID, FEATURE_FLAG_NAME, true, 10);
        job.setId(jobId);

        when(featureFlagService.getJobStatus(jobId)).thenReturn(job);

        // When
        ResponseEntity<FeatureFlagJob> response = featureFlagController.getJobStatus(jobId);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jobId, response.getBody().getId());
        assertEquals(ORG_ID, response.getBody().getOrganizationId());
        assertEquals(FEATURE_FLAG_NAME, response.getBody().getFeatureFlagName());
        assertTrue(response.getBody().isEnabled());

        verify(featureFlagService).getJobStatus(jobId);
    }

    @Test
    void getJobStatus_InvalidJobId_ThrowsNotFoundException() {
        // Given
        Long jobId = 999L;
        when(featureFlagService.getJobStatus(jobId)).thenThrow(new IllegalArgumentException("Job not found: " + jobId));

        // When & Then
        assertThrows(ResponseStatusException.class,
                () -> featureFlagController.getJobStatus(jobId));

        verify(featureFlagService).getJobStatus(jobId);
    }

    @Test
    void getJobsForOrganization_ValidOrgId_ReturnsJobList() {
        // Given
        FeatureFlagJob job1 = new FeatureFlagJob(ORG_ID, FEATURE_FLAG_NAME, true, 5);
        job1.setId(1L);
        FeatureFlagJob job2 = new FeatureFlagJob(ORG_ID, "another-flag", false, 10);
        job2.setId(2L);

        List<FeatureFlagJob> jobs = List.of(job1, job2);
        when(featureFlagService.getJobsForOrganization(ORG_ID)).thenReturn(jobs);

        // When
        ResponseEntity<List<FeatureFlagJob>> response = featureFlagController.getJobsForOrganization(ORG_ID);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals(job1.getId(), response.getBody().get(0).getId());
        assertEquals(job2.getId(), response.getBody().get(1).getId());

        verify(featureFlagService).getJobsForOrganization(ORG_ID);
    }

    @Test
    void retryFailedJob_ValidJobId_ReturnsRetryJobResponse() {
        // Given
        Long originalJobId = 123L;
        Long retryJobId = 124L;
        when(featureFlagService.retryFailedJob(originalJobId)).thenReturn(retryJobId);

        // When
        ResponseEntity<Map<String, Object>> response = featureFlagController.retryFailedJob(originalJobId);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());

        Map<String, Object> responseBody = response.getBody();
        assertEquals(originalJobId, responseBody.get("originalJobId"));
        assertEquals(retryJobId, responseBody.get("retryJobId"));
        assertEquals("Job retry initiated successfully", responseBody.get("message"));
        assertEquals("/job-status/" + retryJobId, responseBody.get("trackingUrl"));

        verify(featureFlagService).retryFailedJob(originalJobId);
    }

    @Test
    void retryFailedJob_JobNotFound_ThrowsBadRequestException() {
        // Given
        Long jobId = 999L;
        when(featureFlagService.retryFailedJob(jobId))
                .thenThrow(new IllegalArgumentException("Job not found: " + jobId));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> featureFlagController.retryFailedJob(jobId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getReason().contains("Job not found"));

        verify(featureFlagService).retryFailedJob(jobId);
    }

    @Test
    void retryFailedJob_CannotRetryJob_ThrowsBadRequestException() {
        // Given
        Long jobId = 123L;
        when(featureFlagService.retryFailedJob(jobId))
                .thenThrow(new IllegalStateException("Can only retry failed jobs. Current status: COMPLETED"));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> featureFlagController.retryFailedJob(jobId));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getReason().contains("Can only retry failed jobs"));

        verify(featureFlagService).retryFailedJob(jobId);
    }

    @Test
    void getJobRetryChain_ValidJobId_ReturnsRetryChain() {
        // Given
        Long jobId = 123L;

        // Create original job
        FeatureFlagJob originalJob = new FeatureFlagJob(ORG_ID, FEATURE_FLAG_NAME, true, 10);
        originalJob.setId(123L);
        originalJob.setRetryCount(3);
        originalJob.setMaxRetries(3);
        originalJob.markAsFailed("Database connection timeout");

        // Create retry job
        FeatureFlagJob retryJob = new FeatureFlagJob(ORG_ID, FEATURE_FLAG_NAME, true, 10, 123L);
        retryJob.setId(124L);
        retryJob.setRetryCount(1);
        retryJob.setMaxRetries(3);
        retryJob.markAsCompleted();

        List<FeatureFlagJob> retryChain = List.of(originalJob, retryJob);
        when(featureFlagService.getJobRetryChain(jobId)).thenReturn(retryChain);

        // When
        ResponseEntity<List<FeatureFlagJob>> response = featureFlagController.getJobRetryChain(jobId);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());

        // Verify original job
        FeatureFlagJob returnedOriginalJob = response.getBody().get(0);
        assertEquals(123L, returnedOriginalJob.getId());
        assertEquals(FeatureFlagJob.JobStatus.FAILED, returnedOriginalJob.getStatus());
        assertEquals(3, returnedOriginalJob.getRetryCount());
        assertNull(returnedOriginalJob.getParentJobId());

        // Verify retry job
        FeatureFlagJob returnedRetryJob = response.getBody().get(1);
        assertEquals(124L, returnedRetryJob.getId());
        assertEquals(FeatureFlagJob.JobStatus.COMPLETED, returnedRetryJob.getStatus());
        assertEquals(1, returnedRetryJob.getRetryCount());
        assertEquals(123L, returnedRetryJob.getParentJobId());

        verify(featureFlagService).getJobRetryChain(jobId);
    }

    @Test
    void getJobRetryChain_JobNotFound_ThrowsNotFoundException() {
        // Given
        Long jobId = 999L;
        when(featureFlagService.getJobRetryChain(jobId))
                .thenThrow(new IllegalArgumentException("Job not found: " + jobId));

        // When & Then
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> featureFlagController.getJobRetryChain(jobId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertTrue(exception.getReason().contains("Job not found"));

        verify(featureFlagService).getJobRetryChain(jobId);
    }

    @Test
    void getJobRetryChain_SingleJob_ReturnsChainWithOneJob() {
        // Given
        Long jobId = 123L;

        FeatureFlagJob singleJob = new FeatureFlagJob(ORG_ID, FEATURE_FLAG_NAME, true, 5);
        singleJob.setId(123L);
        singleJob.setRetryCount(0);
        singleJob.markAsCompleted();

        List<FeatureFlagJob> retryChain = List.of(singleJob);
        when(featureFlagService.getJobRetryChain(jobId)).thenReturn(retryChain);

        // When
        ResponseEntity<List<FeatureFlagJob>> response = featureFlagController.getJobRetryChain(jobId);

        // Then
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());

        FeatureFlagJob returnedJob = response.getBody().get(0);
        assertEquals(123L, returnedJob.getId());
        assertEquals(FeatureFlagJob.JobStatus.COMPLETED, returnedJob.getStatus());
        assertEquals(0, returnedJob.getRetryCount());
        assertNull(returnedJob.getParentJobId());

        verify(featureFlagService).getJobRetryChain(jobId);
    }
}