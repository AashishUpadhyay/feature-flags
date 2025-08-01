# Overview

Feature flags is an application that can be used to manage feature flags that one plans to use in their product. It exposes two simple APIs to toggle feature flag values:

```
GET: https://yourdomain.com/{org-id}/{feature-flag-name}
POST: https://yourdomain.com/{org-id}/{feature-flag-name}/{val}
```

# Functional Requirements

## Feature Flags
  - As an engineer:
    - I should be able to define feature flags in a JSON file
    - The feature flags once defined can be toggled for orgnaization groups
   
    - A feature flag defined for a parent is applicable to all its children

## Organization Groups
  - As an engineer:
    - I should be able to post organizatioon groups in bulk. The incoming data will mention the org and its parent
    - Additionally I should also be able to add a single Organization to an parent


# Non-Functional Requirements

- Scalability

  - The application needs to support up to 1 billion organization groups that can be organized in hierarchies
  - Should handle at least 10,000 requests per second for feature flag lookups
  - Support for at least 100,000 unique feature flags across the system

- Performance

  - Feature flag lookup latency should be under 50ms at p95
  - Feature flag toggle operations should complete within 200ms at p95
  - API endpoints should maintain 99.9% availability

- Security

  - All API endpoints must be authenticated and authorized
  - Support for role-based access control (RBAC) for feature flag management
  - Audit logging for all feature flag changes
  - Data encryption at rest and in transit

- Reliability

  - System should be resilient to partial outages
  - Feature flag states should be eventually consistent across all regions
  - Implement circuit breakers to handle downstream dependencies
  - Maintain backup and disaster recovery with RPO < 1 hour and RTO < 15 minutes

- Observability

  - Comprehensive logging for all feature flag operations
  - Metrics for feature flag usage patterns
  - Alerting system for unusual patterns or system issues
  - Tracing support for debugging and performance monitoring

- Maintainability
  - API versioning support
  - Documentation for all APIs and integration patterns
  - Support for bulk operations and migrations
  - Configuration changes should not require application restarts
