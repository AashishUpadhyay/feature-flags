# Feature Flag Background Processing Guide

## Overview

This system provides background processing for feature flag updates that need to propagate through organizational hierarchies. When setting a feature flag for a parent organization, the system automatically updates all descendant organizations for denormalized storage optimization in a read-heavy service.

## Why Background Processing?

### The Problem

- Organizations can have deep hierarchies (parent → children → grandchildren, etc.)
- Setting a feature flag on a parent organization requires updating ALL descendants
- Large organizations might have hundreds or thousands of descendants
- Synchronous processing would timeout or block the client for too long

### The Solution

- **Immediate Response**: Client gets a response immediately with a job ID
- **Background Processing**: Hierarchy updates happen asynchronously in a dedicated thread pool
- **Progress Tracking**: Real-time status and progress tracking via job ID
- **Error Handling**: Individual failures don't stop the entire process

## Architecture

```
Client Request → Immediate Response (Job ID) → Background Processing → Complete
                      ↓                              ↓
                 Job Created                   Updates All Children
                      ↓                              ↓
                 Status: PENDING              Status: IN_PROGRESS → COMPLETED
```

## API Usage

### Unified Feature Flag Update Endpoint

```bash
POST /{orgId}/{featureFlagName}/{enabled}
```

**Smart Processing**: Automatically determines whether to process immediately or in background based on organization hierarchy.

#### Scenario 1: Organization with No Children (Immediate Processing)

**Request:**

```bash
POST /5/new-checkout-flow/true
```

**Response:** `200 OK` (Synchronous)

```json
{
  "featureFlag": {
    "id": null,
    "name": "new-checkout-flow",
    "enabled": true,
    "organizationId": 5
  },
  "message": "Feature flag updated immediately",
  "processedImmediately": true,
  "jobId": null
}
```

#### Scenario 2: Organization with Children (Background Processing)

**Request:**

```bash
POST /1/new-checkout-flow/true
```

**Response:** `202 Accepted` (Asynchronous)

```json
{
  "message": "Background processing started for organization hierarchy",
  "processedImmediately": false,
  "jobId": 123,
  "trackingUrl": "/job-status/123",
  "organizationId": 1,
  "featureFlagName": "new-checkout-flow",
  "enabled": true
}
```

### Track Job Progress

```bash
GET /job-status/{jobId}
```

#### Example Response:

```json
{
  "id": 123,
  "organizationId": 1,
  "featureFlagName": "new-checkout-flow",
  "enabled": true,
  "status": "IN_PROGRESS",
  "totalOrganizations": 25,
  "processedOrganizations": 15,
  "errorMessage": null,
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:32:00",
  "completedAt": null
}
```

### Get All Jobs for Organization

```bash
GET /jobs/{orgId}
```

### Retry Failed Jobs

#### Manual Retry

```bash
POST /retry-job/{jobId}
```

**Example Response:**

```json
{
  "originalJobId": 123,
  "retryJobId": 125,
  "message": "Job retry initiated successfully",
  "trackingUrl": "/job-status/125"
}
```

#### Get Retry Chain

```bash
GET /job-retry-chain/{jobId}
```

**Example Response:**

```json
[
  {
    "id": 123,
    "status": "FAILED",
    "retryCount": 3,
    "maxRetries": 3,
    "parentJobId": null,
    "errorMessage": "Database connection timeout",
    "createdAt": "2024-01-15T10:30:00"
  },
  {
    "id": 124,
    "status": "FAILED",
    "retryCount": 1,
    "maxRetries": 3,
    "parentJobId": 123,
    "errorMessage": "Network timeout",
    "createdAt": "2024-01-15T10:32:00"
  },
  {
    "id": 125,
    "status": "COMPLETED",
    "retryCount": 2,
    "maxRetries": 3,
    "parentJobId": 123,
    "completedAt": "2024-01-15T10:35:00"
  }
]
```

## Job Statuses

| Status        | Description                                   |
| ------------- | --------------------------------------------- |
| `PENDING`     | Job created, waiting to start processing      |
| `IN_PROGRESS` | Currently processing organizations            |
| `COMPLETED`   | All organizations processed successfully      |
| `FAILED`      | Job failed with error                         |
| `RETRYING`    | Job failed and is being automatically retried |

## Usage Scenarios

### Scenario 1: Small Organization (No Children)

```bash
POST /5/feature-x/true
```

**Result**: Processes immediately, no background job needed
**Status**: `200 OK`

```json
{
  "featureFlag": { "name": "feature-x", "enabled": true, "organizationId": 5 },
  "message": "Feature flag updated immediately",
  "processedImmediately": true,
  "jobId": null
}
```

### Scenario 2: Large Organization Hierarchy

```bash
POST /1/maintenance-mode/true
```

**Result**: Background job created
**Status**: `202 Accepted`

```json
{
  "jobId": 124,
  "message": "Background processing started for organization hierarchy",
  "processedImmediately": false,
  "trackingUrl": "/job-status/124"
}
```

Then track progress:

```bash
GET /job-status/124
```

### Scenario 3: Emergency Feature Toggle

```java
// Emergency disable across entire organization tree
featureFlagService.setFeatureFlagWithHierarchy(1L, "payment-system", false);
```

### Scenario 4: Job Failure with Automatic Retry

```bash
POST /1/checkout-v2/true
```

**Initial Response**: `202 Accepted`

```json
{
  "jobId": 126,
  "message": "Background processing started",
  "trackingUrl": "/job-status/126"
}
```

**Job fails due to temporary database issue:**

```bash
GET /job-status/126
```

```json
{
  "id": 126,
  "status": "RETRYING",
  "retryCount": 1,
  "maxRetries": 3,
  "errorMessage": "Database connection timeout",
  "processedOrganizations": 5,
  "totalOrganizations": 20
}
```

**System automatically creates retry job:**

```bash
GET /job-status/127  # New retry job
```

```json
{
  "id": 127,
  "status": "IN_PROGRESS",
  "retryCount": 1,
  "parentJobId": 126,
  "processedOrganizations": 12,
  "totalOrganizations": 20
}
```

### Scenario 5: Manual Retry After Multiple Failures

```bash
# Original job failed after max retries
GET /job-status/128
```

```json
{
  "id": 128,
  "status": "FAILED",
  "retryCount": 3,
  "maxRetries": 3,
  "errorMessage": "Network connectivity issues"
}
```

```bash
# Manually retry after fixing network issues
POST /retry-job/128
```

```json
{
  "originalJobId": 128,
  "retryJobId": 129,
  "message": "Job retry initiated successfully",
  "trackingUrl": "/job-status/129"
}
```

## Programmatic Usage

### Service Layer

```java
@Autowired
private FeatureFlagService featureFlagService;

// Check if background processing is needed
boolean hasChildren = featureFlagService.organizationHasChildren(orgId);

if (hasChildren) {
    // Use background processing
    Long jobId = featureFlagService.setFeatureFlagWithHierarchy(orgId, flagName, enabled);
    // Store jobId for tracking
} else {
    // Use immediate processing
    featureFlagService.setFeatureFlag(orgId, flagName, enabled);
}
```

### Tracking Progress

```java
// Get job status
FeatureFlagJob job = featureFlagService.getJobStatus(jobId);

// Check if completed
if (job.getStatus() == FeatureFlagJob.JobStatus.COMPLETED) {
    System.out.println("Processing completed!");
} else if (job.getStatus() == FeatureFlagJob.JobStatus.FAILED) {
    if (job.canRetry()) {
        System.out.println("Job failed but can retry. Attempt: " + job.getRetryCount() + "/" + job.getMaxRetries());
    } else {
        System.out.println("Job permanently failed after " + job.getRetryCount() + " retries");
    }
}

// Get progress
double progress = (double) job.getProcessedOrganizations() / job.getTotalOrganizations();
System.out.println("Progress: " + (progress * 100) + "%");
```

### Retry Management

```java
// Manual retry for failed jobs
if (job.getStatus() == FeatureFlagJob.JobStatus.FAILED) {
    try {
        Long retryJobId = featureFlagService.retryFailedJob(jobId);
        System.out.println("Retry job started: " + retryJobId);
    } catch (IllegalStateException e) {
        System.out.println("Cannot retry job: " + e.getMessage());
    }
}

// Get complete retry chain
List<FeatureFlagJob> retryChain = featureFlagService.getJobRetryChain(jobId);
for (FeatureFlagJob retryJob : retryChain) {
    System.out.println("Job " + retryJob.getId() + ": " + retryJob.getStatus()
                     + " (Retry " + retryJob.getRetryCount() + ")");
}
```

## Performance Characteristics

### Thread Pool Configuration

```java
// AsyncConfiguration.java
executor.setCorePoolSize(3);      // Always 3 threads running
executor.setMaxPoolSize(10);      // Max 10 threads under load
executor.setQueueCapacity(50);    // Queue up to 50 jobs
```

### Processing Rate

- **Batch Size**: Progress saved every 10 organizations
- **Individual Failures**: Don't stop entire job
- **Memory Efficient**: Processes organizations one by one
- **Database Optimized**: Uses batch operations where possible

### Retry Mechanism

- **Automatic Retries**: Up to 3 retry attempts per job
- **Exponential Backoff**: 2^retryCount seconds delay (2s, 4s, 8s)
- **Retry Job Creation**: Each retry gets a new job ID linked to original
- **Manual Retry**: Operators can retry failed jobs with additional attempts
- **Retry Tracking**: Complete audit trail of all retry attempts

## Error Handling

### Individual Organization Failures

```log
ERROR - Failed to update feature flag for organization 15 in job 123: Database connection failed
```

- Job continues processing other organizations
- Partial success is tracked
- Error logged but job doesn't fail entirely

### Complete Job Failure

```json
{
  "id": 123,
  "status": "FAILED",
  "retryCount": 3,
  "maxRetries": 3,
  "errorMessage": "Unable to load organization hierarchy",
  "processedOrganizations": 0,
  "completedAt": "2024-01-15T10:35:00"
}
```

### Automatic Retry Scenario

**Initial Failure:**

```log
ERROR - Failed to process job 123: Database connection timeout
INFO  - Job 123 failed but can be retried. Retry count: 1/3
INFO  - Scheduling retry for job 123 in 2 seconds. New retry job ID: 124
```

**Retry Processing:**

```log
INFO  - Starting delayed retry for job 124
INFO  - Starting background processing for job 124 - Organization: 1, Flag: checkout-v2
INFO  - Completed background processing for job 124 - Processed 25/25 organizations
```

### Manual Retry Logs

```log
INFO  - Manual retry initiated for job 123. New retry job ID: 125
INFO  - Starting background processing for job 125 - Organization: 1, Flag: checkout-v2
```

## Monitoring and Logging

### Key Log Messages

```log
INFO  - Starting background processing for job 123 - Organization: 1, Flag: checkout-v2, Enabled: true
INFO  - Found 24 descendant organizations to update for job 123
DEBUG - Job 123 progress: 10/25 organizations processed
INFO  - Completed background processing for job 123 - Processed 25/25 organizations
ERROR - Failed to process job 123: Database connection timeout
INFO  - Job 123 failed but can be retried. Retry count: 1/3
INFO  - Scheduling retry for job 123 in 2 seconds. New retry job ID: 124
INFO  - Starting delayed retry for job 124
WARN  - Retry scheduling interrupted for job 124
ERROR - Job 125 failed and exhausted all retries. Marking as permanently failed.
INFO  - Manual retry initiated for job 123. New retry job ID: 126
```

### Metrics to Monitor

- Average job completion time
- Success/failure rates
- Queue depth
- Thread pool utilization
- **Retry success rates**
- **Average retries per job**
- **Retry delay effectiveness**
- **Manual retry frequency**

## Database Schema

### Feature Flag Jobs Table

```sql
CREATE TABLE feature_flag_jobs (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    feature_flag_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_organizations INTEGER,
    processed_organizations INTEGER DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    parent_job_id BIGINT,

    CONSTRAINT fk_feature_flag_jobs_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_feature_flag_jobs_parent
        FOREIGN KEY (parent_job_id) REFERENCES feature_flag_jobs(id)
);

-- Indexes for better query performance
CREATE INDEX idx_feature_flag_jobs_organization_id ON feature_flag_jobs(organization_id);
CREATE INDEX idx_feature_flag_jobs_status ON feature_flag_jobs(status);
CREATE INDEX idx_feature_flag_jobs_created_at ON feature_flag_jobs(created_at);
CREATE INDEX idx_feature_flag_jobs_parent_job_id ON feature_flag_jobs(parent_job_id);
```

## Best Practices

### 1. Client Implementation

```javascript
// Single endpoint handles both immediate and background processing
const response = await fetch('/1/feature-x/true', { method: 'POST' });
const result = await response.json();

if (result.processedImmediately) {
  // Feature flag updated immediately
  console.log('Feature flag updated:', result.featureFlag);
} else {
  // Background processing started
  console.log('Background job started:', result.jobId);
  pollJobStatus(result.jobId);
}

// Poll for status (only needed for background jobs)
function pollJobStatus(jobId) {
  const checkStatus = async () => {
    const statusResponse = await fetch(`/job-status/${jobId}`);
    const job = await statusResponse.json();

    if (job.status === 'COMPLETED') {
      console.log('Job completed successfully!');
    } else if (job.status === 'FAILED') {
      console.error('Job failed:', job.errorMessage);
      if (job.retryCount < job.maxRetries) {
        console.log('Job will be automatically retried...');
      } else {
        console.log('Job permanently failed after', job.retryCount, 'retries');
      }
    } else if (job.status === 'RETRYING') {
      console.log(
        'Job failed but is being retried. Attempt:',
        job.retryCount + '/' + job.maxRetries
      );
      setTimeout(checkStatus, 2000);
    } else {
      // Still in progress, check again
      setTimeout(checkStatus, 2000); // Check every 2 seconds
    }
  };
  checkStatus();
}
```

### 2. Error Recovery

```java
// Check for failed jobs and implement retry logic
if (job.getStatus() == FeatureFlagJob.JobStatus.FAILED) {
    if (job.canRetry()) {
        // Automatic retry will handle this
        logger.info("Job {} will be automatically retried", job.getId());
    } else {
        // Manual intervention required
        logger.warn("Job {} permanently failed after {} retries",
                   job.getId(), job.getRetryCount());

        // Option 1: Manual retry with additional attempts
        try {
            Long retryJobId = featureFlagService.retryFailedJob(job.getId());
            logger.info("Manual retry initiated: {}", retryJobId);
        } catch (IllegalStateException e) {
            logger.error("Cannot retry job: {}", e.getMessage());
        }

        // Option 2: Create new job (if conditions have changed)
        // Long newJobId = featureFlagService.setFeatureFlagWithHierarchy(
        //     job.getOrganizationId(),
        //     job.getFeatureFlagName(),
        //     job.isEnabled()
        // );
    }
}
```

### 3. Retry Chain Analysis

```java
// Analyze retry patterns for debugging
List<FeatureFlagJob> retryChain = featureFlagService.getJobRetryChain(jobId);

System.out.println("Retry Chain Analysis:");
for (FeatureFlagJob retryJob : retryChain) {
    System.out.printf("Job %d: %s (Retry %d/%d) - %s%n",
        retryJob.getId(),
        retryJob.getStatus(),
        retryJob.getRetryCount(),
        retryJob.getMaxRetries(),
        retryJob.getErrorMessage() != null ? retryJob.getErrorMessage() : "No error"
    );
}

// Calculate success rate
long successfulRetries = retryChain.stream()
    .filter(job -> job.getStatus() == FeatureFlagJob.JobStatus.COMPLETED)
    .count();
double successRate = (double) successfulRetries / retryChain.size() * 100;
System.out.printf("Retry success rate: %.1f%%", successRate);
```

### 4. Cleanup

```java
// Periodically clean up old completed jobs (including retry chains)
@Scheduled(fixedRate = 86400000) // Daily
public void cleanupOldJobs() {
    LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);

    // Delete completed jobs older than 30 days
    jobRepository.deleteByStatusAndCompletedAtBefore(
        FeatureFlagJob.JobStatus.COMPLETED,
        cutoffDate
    );

    // Delete failed jobs with no pending retries older than 7 days
    List<FeatureFlagJob> oldFailedJobs = jobRepository.findByStatusAndCompletedAtBefore(
        FeatureFlagJob.JobStatus.FAILED,
        LocalDateTime.now().minusDays(7)
    );

    for (FeatureFlagJob failedJob : oldFailedJobs) {
        if (!failedJob.canRetry() && failedJob.getParentJobId() == null) {
            // Clean up the entire retry chain for permanently failed root jobs
            List<FeatureFlagJob> retryChain = getJobRetryChain(failedJob.getId());
            jobRepository.deleteAll(retryChain);
        }
    }
}
```

## Retry Mechanism Summary

The retry mechanism provides **comprehensive failure recovery** with:

- ✅ **Automatic Retries**: Up to 3 attempts with exponential backoff
- ✅ **Manual Retries**: Operator intervention for complex failures
- ✅ **Retry Tracking**: Complete audit trail of all attempts
- ✅ **Smart Backoff**: Prevents cascade failures with delays (2s, 4s, 8s)
- ✅ **Job Linkage**: Parent-child relationships between original and retry jobs
- ✅ **Progress Preservation**: Retry jobs start fresh but track attempt count
- ✅ **Operational Visibility**: Full retry chain analysis and monitoring

This background processing system ensures that large hierarchical feature flag updates don't block clients while providing **high reliability**, **full transparency**, and **comprehensive failure recovery** for the update process.
