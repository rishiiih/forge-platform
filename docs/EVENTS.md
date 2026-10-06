# Forge Event Contracts

## 1. Purpose

Forge uses Kafka as its asynchronous event backbone.

Kafka events communicate facts that have already occurred in a Forge domain. Kafka is not the authoritative source of business state.

PostgreSQL remains the authoritative source of current business state.

The event system follows these principles:

- Events are immutable facts.
- Events are delivered at least once.
- Consumers must be idempotent.
- Every event has a globally unique `eventId`.
- Events carry correlation and causation information.
- Domain state changes and creation of important events use the Transactional Outbox pattern.
- Consumers must never assume that an event is delivered exactly once.
- Event schemas are versioned.
- Events must contain enough information for consumers to process them without querying another service merely to understand what happened.

---

# 2. Event Envelope

Every Forge Kafka event uses the following envelope:

```json
{
  "eventId": "01J...",
  "eventType": "BuildRequested",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T10:30:00Z",
  "producer": "control-plane-service",
  "correlationId": "01J...",
  "causationId": "01J...",
  "organizationId": "01J...",
  "payload": {}
}
```

## Fields

| Field | Type | Required | Description |
|---|---|---:|---|
| `eventId` | UUID/ULID | Yes | Globally unique identifier for this event |
| `eventType` | string | Yes | Domain event name |
| `eventVersion` | integer | Yes | Version of this event schema |
| `occurredAt` | ISO-8601 timestamp | Yes | Time the domain event occurred |
| `producer` | string | Yes | Service that produced the event |
| `correlationId` | UUID/ULID | Yes | Identifies the complete request/workflow |
| `causationId` | UUID/ULID | No | Event/request that directly caused this event |
| `organizationId` | UUID/ULID | Usually | Organization associated with the event |
| `payload` | object | Yes | Event-specific data |

---

# 3. Event Identity

`eventId` is the identity of the event itself.

It must not be reused.

If an event is retried or republished because of a publisher failure, the original event identity is preserved where possible.

Consumers use `eventId` for deduplication.

Example:

```text
eventId = 01K...

Consumer receives event
        ↓
Does 01K... already exist in processed_events?
        ↓
YES → ignore safely
NO  → process event
       ↓
       record eventId
```

---

# 4. Correlation and Causation

## correlationId

`correlationId` identifies the complete business workflow.

Example:

```text
GitHub webhook
      ↓
BuildRequested
      ↓
BuildStarted
      ↓
BuildSucceeded
      ↓
DeploymentRequested
      ↓
DeploymentSucceeded
```

All events belonging to the same workflow can share a correlation ID.

This allows Forge to trace a workflow across services.

## causationId

`causationId` identifies the immediate event or request that caused the current event.

Example:

```text
BuildRequested
eventId = A

        ↓

BuildStarted
causationId = A
eventId = B
```

---

# 5. Kafka Topics

Forge initially uses the following logical topics.

## `forge.build.events`

Build lifecycle events.

Events:

- `BuildRequested`
- `BuildStarted`
- `BuildSucceeded`
- `BuildFailed`
- `BuildCancelled`
- `BuildTimedOut`

## `forge.deployment.events`

Deployment lifecycle events.

Events:

- `DeploymentRequested`
- `DeploymentStarted`
- `DeploymentHealthCheckPassed`
- `DeploymentHealthCheckFailed`
- `DeploymentSucceeded`
- `DeploymentFailed`
- `DeploymentCancelled`
- `DeploymentTimedOut`
- `DeploymentRolledBack`

## `forge.project.events`

Project lifecycle events.

Events:

- `ProjectCreated`
- `ProjectUpdated`
- `ProjectDeletionRequested`
- `ProjectDeleted`

## `forge.audit.events`

Security and audit events.

Examples:

- `UserCreated`
- `OrganizationCreated`
- `OrganizationMemberAdded`
- `OrganizationMemberRemoved`
- `RoleChanged`
- `SecretCreated`
- `SecretRotated`
- `SecretDeleted`

Audit events are facts emitted by the service that owns the operation.

---

# 6. Build Events

## BuildRequested

Produced when a build is requested.

```json
{
  "eventType": "BuildRequested",
  "eventVersion": 1,
  "payload": {
    "buildId": "01J...",
    "projectId": "01J...",
    "repository": "github.com/example/project",
    "commitSha": "abc123...",
    "branch": "main",
    "dockerfilePath": "Dockerfile",
    "buildContext": "."
  }
}
```

## BuildStarted

Produced when a build worker begins execution.

```json
{
  "eventType": "BuildStarted",
  "eventVersion": 1,
  "payload": {
    "buildId": "01J...",
    "projectId": "01J...",
    "workerId": "worker-01"
  }
}
```

## BuildSucceeded

Produced when the image is successfully built and pushed.

```json
{
  "eventType": "BuildSucceeded",
  "eventVersion": 1,
  "payload": {
    "buildId": "01J...",
    "projectId": "01J...",
    "commitSha": "abc123...",
    "artifactDigest": "sha256:abc..."
  }
}
```

The artifact digest is immutable and becomes the authoritative deployment artifact.

## BuildFailed

```json
{
  "eventType": "BuildFailed",
  "eventVersion": 1,
  "payload": {
    "buildId": "01J...",
    "projectId": "01J...",
    "reason": "BUILD_COMMAND_FAILED",
    "retryable": false
  }
}
```

---

# 7. Deployment Events

## DeploymentRequested

```json
{
  "eventType": "DeploymentRequested",
  "eventVersion": 1,
  "payload": {
    "deploymentId": "01J...",
    "projectId": "01J...",
    "environmentId": "01J...",
    "artifactDigest": "sha256:abc...",
    "commitSha": "abc123...",
    "configurationSnapshotId": "01J...",
    "trigger": "AUTOMATIC"
  }
}
```

## DeploymentStarted

```json
{
  "eventType": "DeploymentStarted",
  "eventVersion": 1,
  "payload": {
    "deploymentId": "01J...",
    "environmentId": "01J...",
    "artifactDigest": "sha256:abc..."
  }
}
```

## DeploymentHealthCheckPassed

```json
{
  "eventType": "DeploymentHealthCheckPassed",
  "eventVersion": 1,
  "payload": {
    "deploymentId": "01J...",
    "environmentId": "01J...",
    "attempt": 1
  }
}
```

## DeploymentSucceeded

This event is emitted only after the new version has successfully passed health checks and traffic has been switched.

```json
{
  "eventType": "DeploymentSucceeded",
  "eventVersion": 1,
  "payload": {
    "deploymentId": "01J...",
    "environmentId": "01J...",
    "artifactDigest": "sha256:abc..."
  }
}
```

## DeploymentFailed

```json
{
  "eventType": "DeploymentFailed",
  "eventVersion": 1,
  "payload": {
    "deploymentId": "01J...",
    "environmentId": "01J...",
    "reason": "HEALTH_CHECK_FAILED",
    "retryable": false
  }
}
```

---

# 8. Event Delivery Semantics

Forge uses **at-least-once delivery**.

A consumer may receive the same event more than once.

Therefore:

```text
Kafka
  ↓
Consumer
  ↓
Check eventId
  ↓
Already processed?
  ├── Yes → acknowledge/ignore
  └── No
       ↓
   Process event
       ↓
   Record eventId
```

Consumers must be designed so that processing an event twice does not produce incorrect business state.

---

# 9. Consumer Idempotency

Each service that consumes events maintains an idempotency/processed-event record.

Conceptually:

```text
processed_events
-----------------------------
consumer_name
event_id
processed_at
```

Unique constraint:

```text
(consumer_name, event_id)
```

This prevents the same event from producing duplicate side effects.

Example:

```text
BuildSucceeded
      ↓
Deployment Service

First delivery:
eventId = X
→ process
→ record X

Second delivery:
eventId = X
→ X already recorded
→ do not process again
```

---

# 10. Transactional Outbox

Important domain events must not be published using:

```text
UPDATE database
    ↓
Kafka publish
```

because the application can crash between those operations.

Instead:

```text
Database transaction
        │
        ├── update business state
        │
        └── insert outbox event
                  │
                  ↓
             COMMIT
                  │
                  ↓
           Outbox Publisher
                  │
                  ↓
                Kafka
```

The business state and outbox record therefore commit atomically.

---

# 11. Outbox Record

The initial outbox model contains:

```text
outbox_events
-----------------------------
id
event_id
event_type
event_version
aggregate_type
aggregate_id
organization_id
payload
occurred_at
created_at
published_at
attempt_count
last_error
```

`event_id` is unique.

The publisher can safely retry unpublished events.

---

# 12. Publisher Failure

If Kafka is temporarily unavailable:

```text
PostgreSQL
    ↓
outbox_events
    ↓
Publisher attempts publish
    ↓
Kafka unavailable
    ↓
record retry state
    ↓
retry later
```

The original business transaction does not need to be rolled back merely because Kafka is unavailable.

This separates business-state durability from Kafka availability.

---

# 13. Event Ordering

Forge does not assume global ordering across Kafka.

Ordering is required only where the domain requires it.

Events for the same aggregate should use a stable Kafka partition key.

Examples:

```text
buildId
deploymentId
projectId
```

Example:

```text
Kafka key = deploymentId
```

This allows deployment lifecycle events for the same deployment to remain ordered within their partition.

---

# 14. Schema Evolution

Events are versioned.

Example:

```text
BuildSucceeded v1
BuildSucceeded v2
```

Consumers must be able to safely process supported versions.

Breaking changes require a new event version.

Existing consumers must not be silently broken by adding incompatible fields.

---

# 15. What Kafka Does Not Own

Kafka does not become Forge's database.

Kafka is not authoritative for:

- current deployment status
- current project configuration
- organization membership
- RBAC
- secrets
- artifact retention state
- current production version

Those states belong to their owning services and PostgreSQL.

Kafka represents:

> What happened.

PostgreSQL represents:

> What is currently true.

---

# 16. Initial Event Flow

The first end-to-end Forge workflow is:

```text
GitHub Webhook
      ↓
Control Plane
      ↓
PostgreSQL Transaction
      ├── create Build
      └── create BuildRequested outbox event
                ↓
             COMMIT
                ↓
         Outbox Publisher
                ↓
              Kafka
                ↓
        Build Service
                ↓
          Build Worker
                ↓
       BuildKit / OCI Registry
                ↓
        BuildSucceeded
                ↓
              Kafka
                ↓
       Deployment Service
```

This flow becomes the first distributed workflow implemented by Forge.

---

# 17. Initial Reliability Guarantees

The event architecture must guarantee:

1. Important business state changes and their outbox events commit atomically.
2. Kafka outages do not lose committed business events.
3. Duplicate event delivery does not create duplicate side effects.
4. Consumers can safely retry failed processing.
5. Event ordering is preserved where required by aggregate partitioning.
6. Events contain correlation and causation information.
7. Event schemas are versioned.
8. Kafka is not treated as the authoritative business database.
9. Consumers do not directly modify another service's database.
10. Every asynchronous workflow has a recoverable failure path.

---

# 18. Initial Scope

The first implementation intentionally does not introduce:

- Kafka Streams
- Schema Registry
- Kafka Connect
- multiple Kafka clusters
- cross-region replication
- event sourcing
- CQRS everywhere

These can be introduced only when a concrete Forge requirement justifies them.

The goal is to demonstrate reliable event-driven architecture, not infrastructure quantity.