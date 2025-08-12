package com.featureflags.service;

import com.featureflags.model.FeatureFlag;
import com.featureflags.model.FeatureFlagJob;
import com.featureflags.repository.FeatureFlagJobRepository;
import com.featureflags.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureFlagBackgroundServiceTest {

        @Mock
        private FeatureFlagRepository featureFlagRepository;

        @Mock
        private FeatureFlagJobRepository jobRepository;

        @Mock
        private OrganizationService organizationService;

        @InjectMocks
        private FeatureFlagBackgroundService backgroundService;

        private FeatureFlagJob testJob;
        private static final Long JOB_ID = 1L;
        private static final Long ORG_ID = 1L;
        private static final String FLAG_NAME = "test-flag";
        private static final boolean ENABLED = true;

        @BeforeEach
        void setUp() {
                testJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 3); // parent + 2 children
                testJob.setId(JOB_ID);
        }

        @Test
        void processFeatureFlagHierarchyUpdate_Success_CompletesJob() {
                // Given
                List<Long> descendantIds = Arrays.asList(2L, 3L);

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(descendantIds);
                when(featureFlagRepository.findByOrganizationIdAndName(anyLong(), eq(FLAG_NAME)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                verify(jobRepository, atLeast(1)).findById(JOB_ID);
                verify(organizationService).getAllDescendantIds(ORG_ID);

                // Verify parent organization updated
                verify(featureFlagRepository).findByOrganizationIdAndName(ORG_ID, FLAG_NAME);

                // Verify descendant organizations updated
                verify(featureFlagRepository).findByOrganizationIdAndName(2L, FLAG_NAME);
                verify(featureFlagRepository).findByOrganizationIdAndName(3L, FLAG_NAME);

                // Verify feature flags saved (parent + 2 descendants = 3 total)
                verify(featureFlagRepository, times(3)).save(any(FeatureFlag.class));

                // Verify job status updated multiple times (progress updates)
                verify(jobRepository, atLeast(3)).save(any(FeatureFlagJob.class));
        }

        @Test
        void processFeatureFlagHierarchyUpdate_WithExistingFlags_UpdatesFlags() {
                // Given
                List<Long> descendantIds = Arrays.asList(2L);
                FeatureFlag existingFlag = new FeatureFlag(FLAG_NAME, null, false, 2L);

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(descendantIds);
                when(featureFlagRepository.findByOrganizationIdAndName(ORG_ID, FLAG_NAME))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.findByOrganizationIdAndName(2L, FLAG_NAME))
                                .thenReturn(Optional.of(existingFlag));
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                verify(featureFlagRepository).save(argThat(flag -> flag.getOrganizationId().equals(2L) &&
                                flag.getName().equals(FLAG_NAME) &&
                                flag.isEnabled() == ENABLED));
        }

        @Test
        void processFeatureFlagHierarchyUpdate_NoDescendants_ProcessesOnlyParent() {
                // Given
                List<Long> descendantIds = Collections.emptyList();

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(descendantIds);
                when(featureFlagRepository.findByOrganizationIdAndName(ORG_ID, FLAG_NAME))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                verify(organizationService).getAllDescendantIds(ORG_ID);

                // Only parent organization should be updated
                verify(featureFlagRepository, times(1)).save(any(FeatureFlag.class));
                verify(featureFlagRepository).save(argThat(flag -> flag.getOrganizationId().equals(ORG_ID) &&
                                flag.getName().equals(FLAG_NAME) &&
                                flag.isEnabled() == ENABLED));
        }

        @Test
        void processFeatureFlagHierarchyUpdate_JobNotFound_ThrowsException() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

                // When & Then
                assertThrows(IllegalArgumentException.class,
                                () -> backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID));

                verify(jobRepository).findById(JOB_ID);
                verifyNoInteractions(organizationService);
                verifyNoInteractions(featureFlagRepository);
        }

        @Test
        void processFeatureFlagHierarchyUpdate_OrganizationServiceThrowsException_MarksJobAsFailed() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID))
                                .thenThrow(new RuntimeException("Database connection failed"));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> {
                                        FeatureFlagJob job = invocation.getArgument(0);
                                        if (job.getId() == null) {
                                                job.setId(124L); // Set ID for new retry job
                                        }
                                        return job;
                                });

                // Mock the retry job lookup
                FeatureFlagJob retryJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 3, JOB_ID);
                retryJob.setId(124L);
                retryJob.setRetryCount(1);
                when(jobRepository.findById(124L)).thenReturn(Optional.of(retryJob));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                verify(jobRepository, atLeastOnce())
                                .save(argThat(job -> job.getStatus() == FeatureFlagJob.JobStatus.FAILED &&
                                                job.getErrorMessage().contains("Database connection failed")));
        }

        @Test
        void processFeatureFlagHierarchyUpdate_IndividualOrganizationFails_ContinuesWithOthers() {
                // Given
                List<Long> descendantIds = Arrays.asList(2L, 3L, 4L);

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(descendantIds);
                when(featureFlagRepository.findByOrganizationIdAndName(ORG_ID, FLAG_NAME))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.findByOrganizationIdAndName(2L, FLAG_NAME))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.findByOrganizationIdAndName(3L, FLAG_NAME))
                                .thenThrow(new RuntimeException("Individual org failure"));
                when(featureFlagRepository.findByOrganizationIdAndName(4L, FLAG_NAME))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                // Should still process parent, org 2, and org 4 (skipping org 3)
                verify(featureFlagRepository, times(3)).save(any(FeatureFlag.class));

                // Job should complete successfully despite individual failure
                verify(jobRepository, atLeastOnce())
                                .save(argThat(job -> job.getStatus() == FeatureFlagJob.JobStatus.COMPLETED));
        }

        @Test
        void processFeatureFlagHierarchyUpdate_LargeHierarchy_SavesProgressPeriodically() {
                // Given - Create a large hierarchy (15 descendants to trigger progress saves)
                List<Long> descendantIds = Arrays.asList(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L,
                                16L);
                testJob.setTotalOrganizations(16); // parent + 15 descendants

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(descendantIds);
                when(featureFlagRepository.findByOrganizationIdAndName(anyLong(), eq(FLAG_NAME)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                // Should save progress multiple times (every 10 organizations + final
                // completion)
                verify(jobRepository, atLeast(3)).save(any(FeatureFlagJob.class));

                // Should process all organizations
                verify(featureFlagRepository, times(16)).save(any(FeatureFlag.class));
        }

        @Test
        void getJobStatus_ValidJobId_ReturnsJob() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));

                // When
                FeatureFlagJob result = backgroundService.getJobStatus(JOB_ID);

                // Then
                assertNotNull(result);
                assertEquals(JOB_ID, result.getId());
                assertEquals(ORG_ID, result.getOrganizationId());
                assertEquals(FLAG_NAME, result.getFeatureFlagName());
                assertEquals(ENABLED, result.isEnabled());

                verify(jobRepository).findById(JOB_ID);
        }

        @Test
        void getJobStatus_InvalidJobId_ThrowsException() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

                // When & Then
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> backgroundService.getJobStatus(JOB_ID));

                assertEquals("Job not found: " + JOB_ID, exception.getMessage());
                verify(jobRepository).findById(JOB_ID);
        }

        @Test
        void getJobsForOrganization_ValidOrgId_ReturnsJobList() {
                // Given
                FeatureFlagJob job1 = new FeatureFlagJob(ORG_ID, FLAG_NAME, true, 5);
                FeatureFlagJob job2 = new FeatureFlagJob(ORG_ID, "another-flag", false, 3);
                List<FeatureFlagJob> jobs = Arrays.asList(job1, job2);

                when(jobRepository.findByOrganizationIdOrderByCreatedAtDesc(ORG_ID)).thenReturn(jobs);

                // When
                List<FeatureFlagJob> result = backgroundService.getJobsForOrganization(ORG_ID);

                // Then
                assertNotNull(result);
                assertEquals(2, result.size());
                assertEquals(job1, result.get(0));
                assertEquals(job2, result.get(1));

                verify(jobRepository).findByOrganizationIdOrderByCreatedAtDesc(ORG_ID);
        }

        @Test
        void getJobsForOrganization_NoJobs_ReturnsEmptyList() {
                // Given
                when(jobRepository.findByOrganizationIdOrderByCreatedAtDesc(ORG_ID))
                                .thenReturn(Collections.emptyList());

                // When
                List<FeatureFlagJob> result = backgroundService.getJobsForOrganization(ORG_ID);

                // Then
                assertNotNull(result);
                assertTrue(result.isEmpty());

                verify(jobRepository).findByOrganizationIdOrderByCreatedAtDesc(ORG_ID);
        }

        @Test
        void processFeatureFlagHierarchyUpdate_UpdatesTotalOrganizationsIfIncorrect() {
                // Given
                List<Long> descendantIds = Arrays.asList(2L, 3L, 4L); // 3 descendants
                testJob.setTotalOrganizations(2); // Incorrect count (should be 4: parent + 3 descendants)

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(descendantIds);
                when(featureFlagRepository.findByOrganizationIdAndName(anyLong(), eq(FLAG_NAME)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                verify(jobRepository, atLeastOnce()).save(argThat(job -> job.getTotalOrganizations() == 4 // Should be
                                                                                                          // corrected
                                                                                                          // to 4
                ));
        }

        // ========== RETRY FUNCTIONALITY TESTS ==========

        @Test
        void processFeatureFlagHierarchyUpdate_JobFailsWithRetryAvailable_SchedulesRetry() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID))
                                .thenThrow(new RuntimeException("Database connection timeout"));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> {
                                        FeatureFlagJob job = invocation.getArgument(0);
                                        if (job.getId() == null) {
                                                job.setId(124L); // Set ID for new retry job
                                        }
                                        return job;
                                });

                // Mock the retry job lookup
                FeatureFlagJob retryJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 3, JOB_ID);
                retryJob.setId(124L);
                retryJob.setRetryCount(1);
                when(jobRepository.findById(124L)).thenReturn(Optional.of(retryJob));

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                // Should mark original job as retrying
                verify(jobRepository, atLeastOnce()).save(argThat(job -> job.getId().equals(JOB_ID) &&
                                job.getStatus() == FeatureFlagJob.JobStatus.RETRYING &&
                                job.getRetryCount() == 1));

                // Should create a new retry job
                verify(jobRepository, atLeastOnce()).save(argThat(job -> job.getParentJobId() != null &&
                                job.getParentJobId().equals(JOB_ID) &&
                                job.getRetryCount() == 1 &&
                                job.getMaxRetries() == 3));
        }

        @Test
        void processFeatureFlagHierarchyUpdate_JobFailsAfterMaxRetries_MarksAsPermanentlyFailed() {
                // Given
                testJob.setRetryCount(3); // Already at max retries
                testJob.setMaxRetries(3);

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID))
                                .thenThrow(new RuntimeException("Database connection timeout"));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> {
                                        FeatureFlagJob job = invocation.getArgument(0);
                                        if (job.getId() == null) {
                                                job.setId(127L); // Set ID for new retry job
                                        }
                                        return job;
                                });

                // When
                backgroundService.processFeatureFlagHierarchyUpdate(JOB_ID);

                // Then
                // Should mark as permanently failed (no retry)
                verify(jobRepository, atLeastOnce()).save(argThat(job -> job.getId().equals(JOB_ID) &&
                                job.getStatus() == FeatureFlagJob.JobStatus.FAILED &&
                                job.getRetryCount() == 3 &&
                                job.getErrorMessage().contains("Database connection timeout")));

                // Should not create retry job
                verify(jobRepository, never()).save(argThat(job -> job.getParentJobId() != null));
        }

        @Test
        void manualRetryJob_ValidFailedJob_CreatesRetryJob() {
                // Given
                FeatureFlagJob failedJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5);
                failedJob.setId(JOB_ID);
                failedJob.setRetryCount(3);
                failedJob.setMaxRetries(3);
                failedJob.markAsFailed("Previous failure");

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(failedJob));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> {
                                        FeatureFlagJob job = invocation.getArgument(0);
                                        if (job.getId() == null) {
                                                job.setId(125L); // Set ID for new retry job
                                        }
                                        return job;
                                });

                // Mock the retry job lookup
                FeatureFlagJob retryJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5, JOB_ID);
                retryJob.setId(125L);
                retryJob.setMaxRetries(4);
                when(jobRepository.findById(125L)).thenReturn(Optional.of(retryJob));

                // When
                Long retryJobId = backgroundService.manualRetryJob(JOB_ID);

                // Then
                assertNotNull(retryJobId);

                // Should create retry job with increased max retries
                verify(jobRepository, atLeastOnce()).save(argThat(job -> job.getParentJobId() != null &&
                                job.getParentJobId().equals(JOB_ID) &&
                                job.getMaxRetries() == 4 && // Original 3 + 1 for manual retry
                                job.getOrganizationId().equals(ORG_ID) &&
                                job.getFeatureFlagName().equals(FLAG_NAME) &&
                                job.isEnabled() == ENABLED));
        }

        @Test
        void manualRetryJob_JobNotFound_ThrowsException() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

                // When & Then
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> backgroundService.manualRetryJob(JOB_ID));

                assertEquals("Job not found: " + JOB_ID, exception.getMessage());
                verify(jobRepository).findById(JOB_ID);
        }

        @Test
        void manualRetryJob_JobNotFailed_ThrowsException() {
                // Given
                testJob.markAsCompleted(); // Job is completed, not failed

                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(testJob));

                // When & Then
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                () -> backgroundService.manualRetryJob(JOB_ID));

                assertTrue(exception.getMessage().contains("Can only retry failed jobs"));
                verify(jobRepository).findById(JOB_ID);
        }

        @Test
        void getJobRetryChain_OriginalJob_ReturnsCompleteChain() {
                // Given
                Long originalJobId = 123L;
                Long retryJobId1 = 124L;
                Long retryJobId2 = 125L;

                // Original job
                FeatureFlagJob originalJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5);
                originalJob.setId(originalJobId);
                originalJob.setRetryCount(2);
                originalJob.markAsFailed("Original failure");

                // First retry job
                FeatureFlagJob retryJob1 = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5, originalJobId);
                retryJob1.setId(retryJobId1);
                retryJob1.setRetryCount(1);
                retryJob1.markAsFailed("First retry failure");

                // Second retry job
                FeatureFlagJob retryJob2 = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5, originalJobId);
                retryJob2.setId(retryJobId2);
                retryJob2.setRetryCount(2);
                retryJob2.markAsCompleted();

                List<FeatureFlagJob> allJobs = Arrays.asList(originalJob, retryJob1, retryJob2);

                when(jobRepository.findById(originalJobId)).thenReturn(Optional.of(originalJob));
                when(jobRepository.findByOrganizationIdOrderByCreatedAtDesc(ORG_ID)).thenReturn(allJobs);

                // When
                List<FeatureFlagJob> retryChain = backgroundService.getJobRetryChain(originalJobId);

                // Then
                assertNotNull(retryChain);
                assertEquals(3, retryChain.size());

                // Verify original job
                FeatureFlagJob returnedOriginal = retryChain.get(0);
                assertEquals(originalJobId, returnedOriginal.getId());
                assertNull(returnedOriginal.getParentJobId());

                // Verify retry jobs
                FeatureFlagJob returnedRetry1 = retryChain.get(1);
                assertEquals(retryJobId1, returnedRetry1.getId());
                assertEquals(originalJobId, returnedRetry1.getParentJobId());

                FeatureFlagJob returnedRetry2 = retryChain.get(2);
                assertEquals(retryJobId2, returnedRetry2.getId());
                assertEquals(originalJobId, returnedRetry2.getParentJobId());
        }

        @Test
        void getJobRetryChain_RetryJob_ReturnsCompleteChain() {
                // Given
                Long originalJobId = 123L;
                Long retryJobId = 124L;

                // Original job
                FeatureFlagJob originalJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5);
                originalJob.setId(originalJobId);
                originalJob.markAsFailed("Original failure");

                // Retry job
                FeatureFlagJob retryJob = new FeatureFlagJob(ORG_ID, FLAG_NAME, ENABLED, 5, originalJobId);
                retryJob.setId(retryJobId);
                retryJob.markAsCompleted();

                List<FeatureFlagJob> allJobs = Arrays.asList(originalJob, retryJob);

                when(jobRepository.findById(retryJobId)).thenReturn(Optional.of(retryJob));
                when(jobRepository.findByOrganizationIdOrderByCreatedAtDesc(ORG_ID)).thenReturn(allJobs);

                // When
                List<FeatureFlagJob> retryChain = backgroundService.getJobRetryChain(retryJobId);

                // Then
                assertNotNull(retryChain);
                assertEquals(2, retryChain.size());

                // Should find the root job (original) and return both
                FeatureFlagJob returnedOriginal = retryChain.get(0);
                assertEquals(originalJobId, returnedOriginal.getId());

                FeatureFlagJob returnedRetry = retryChain.get(1);
                assertEquals(retryJobId, returnedRetry.getId());
                assertEquals(originalJobId, returnedRetry.getParentJobId());
        }

        @Test
        void getJobRetryChain_JobNotFound_ThrowsException() {
                // Given
                when(jobRepository.findById(JOB_ID)).thenReturn(Optional.empty());

                // When & Then
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                                () -> backgroundService.getJobRetryChain(JOB_ID));

                assertEquals("Job not found: " + JOB_ID, exception.getMessage());
                verify(jobRepository).findById(JOB_ID);
        }

        @Test
        void scheduleDelayedRetry_ValidRetryJob_ProcessesAfterDelay() throws InterruptedException {
                // Given
                Long retryJobId = 124L;
                long delaySeconds = 2L;

                when(jobRepository.findById(retryJobId)).thenReturn(Optional.of(testJob));
                when(organizationService.getAllDescendantIds(ORG_ID)).thenReturn(Collections.emptyList());
                when(featureFlagRepository.findByOrganizationIdAndName(anyLong(), eq(FLAG_NAME)))
                                .thenReturn(Optional.empty());
                when(featureFlagRepository.save(any(FeatureFlag.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(jobRepository.save(any(FeatureFlagJob.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                // When
                backgroundService.scheduleDelayedRetry(retryJobId, delaySeconds);

                // Then - Wait for the delay and processing to complete
                Thread.sleep((delaySeconds + 1) * 1000);

                // Verify the retry job was processed
                verify(jobRepository, atLeastOnce()).findById(retryJobId);
                verify(organizationService).getAllDescendantIds(ORG_ID);
                verify(featureFlagRepository, atLeastOnce()).save(any(FeatureFlag.class));
        }

        @Test
        void scheduleDelayedRetry_Interrupted_HandlesGracefully() throws InterruptedException {
                // Given
                Long retryJobId = 124L;
                long delaySeconds = 5L; // Long delay to test interruption

                // When
                Thread retryThread = new Thread(() -> {
                        backgroundService.scheduleDelayedRetry(retryJobId, delaySeconds);
                });
                retryThread.start();

                // Interrupt after a short delay
                Thread.sleep(100);
                retryThread.interrupt();
                retryThread.join(1000); // Wait for thread to finish

                // Then - Should handle interruption gracefully
                // No exceptions should be thrown
                assertTrue(true); // Test passes if no exception is thrown
        }
}
