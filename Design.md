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

# System Design

## Feature Flag Inheritance Design

### Overview

The system uses a **denormalized storage approach** for feature flag inheritance to optimize for read-heavy workloads. Given that feature flag writes are infrequent compared to reads, we prioritize read performance over write complexity.

### Key Constraints

- **Organization hierarchy depth**: Maximum 7 levels
- **Read/Write ratio**: Read-heavy workload with infrequent writes
- **Performance target**: <50ms p95 latency for flag lookups

### Storage Strategy

#### Denormalized Flag Storage

- Each organization stores **all effective feature flags** including inherited ones
- When a flag is set at any level, it propagates down to all descendant organizations
- This eliminates the need for hierarchy traversal during reads

#### Data Model Changes

The current `FeatureFlag` model will be enhanced to support:

- **Source tracking**: Track which organization originally defined the flag
- **Inheritance chain**: Optional metadata about the inheritance path
- **Override capability**: Child organizations can override inherited flags

#### Example Storage Pattern

```
Org Hierarchy: Root(1) -> Division(2) -> Team(3)
Flag: "new_ui" = true set at Division(2)

Stored flags:
- Org 2: {name: "new_ui", enabled: true, source_org_id: 2, local: true}
- Org 3: {name: "new_ui", enabled: true, source_org_id: 2, local: false}
```

### Write Operations (Flag Updates)

#### Flag Creation/Update Process

1. **Validate operation**: Ensure organization exists and user has permissions
2. **Update target organization**: Set/update flag for the specified organization
3. **Propagate to descendants**:
   - Find all descendant organizations (max 7 levels deep)
   - For each descendant without a local override, update inherited flag
   - Batch database operations for efficiency

#### Hierarchy Traversal for Writes

- Use recursive CTE queries or iterative traversal (given 7-level limit)
- Batch updates to minimize database round trips
- Consider async processing for large subtrees

### Read Operations (Flag Lookups)

#### Optimized Read Path

- **Single database lookup**: `SELECT * FROM feature_flags WHERE organization_id = ? AND name = ?`
- **No hierarchy traversal needed**: All effective flags are pre-computed and stored
- **Fast response times**: Achieves <50ms p95 latency target

### Consistency Model

#### Write Consistency

- **Immediate consistency** for the target organization
- **Eventual consistency** for descendant organizations (acceptable given infrequent writes)
- **Conflict resolution**: Local flags always override inherited flags

#### Override Behavior

- Child organizations can explicitly set flags to override inherited values
- Local flags are marked with `local: true` to prevent overwriting during propagation
- Deletion of local flags restores inherited values

### Implementation Considerations

#### Database Optimizations

- **Indexed lookups**: Index on (organization_id, name) for fast reads
- **Hierarchy indexing**: Specialized indexes for ancestor/descendant queries
- **Batch operations**: Use batch inserts/updates for propagation

#### Memory and Storage Trade-offs

- **Storage increase**: ~7x storage overhead in worst case (flags at root level)
- **Read performance**: Significant improvement in read latency
- **Acceptable trade-off**: Given read-heavy workload and storage cost trends

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
