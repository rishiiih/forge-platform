# ADR-002 — Kafka as Event Backbone

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Use Apache Kafka for asynchronous domain events

---

## 1. Context

Forge contains workflows that do not require synchronous request/response communication.

For example:

```text
GitHub Push
    ↓
Build
    ↓
Artifact
    ↓
Deployment
    ↓
Runtime
```

Each step may take seconds or minutes and may involve different services.

A tightly coupled synchronous chain would increase failure propagation.

For example:

```text
Control Plane
    ↓
Build Service
    ↓
Deployment Service
    ↓
Runtime Service
```

would mean a downstream service outage could directly block upstream requests.

Forge therefore requires an asynchronous communication mechanism.

---

## 2. Decision

Apache Kafka will be used as Forge's event backbone.

Kafka will transport domain events between services.

Examples:

```text
CommitPushed
BuildRequested
BuildStarted
BuildCompleted
BuildFailed

DeploymentRequested
DeploymentStarted
DeploymentSucceeded
DeploymentFailed
DeploymentRolledBack

RuntimeHealthChanged
SecretRotated
ProjectCreated
```

---

## 3. Kafka Is Not the Source of Truth

Kafka does not own Forge's authoritative business state.

PostgreSQL remains authoritative.

The distinction is:

```text
PostgreSQL
"What is the current state?"

Kafka
"What happened?"
```

For example:

```text
Deployment
status = SUCCESS
```

is stored in PostgreSQL.

The event:

```text
DeploymentSucceeded
```

is published to Kafka so other systems can react.

---

## 4. Event Delivery

Consumers must assume at-least-once delivery.

Therefore:

```text
Same event
     ↓
May be delivered twice
     ↓
Consumer must remain safe
```

Consumers must implement idempotency.

---

## 5. Example

Suppose the Deployment Service receives:

```text
DeploymentRequested
eventId = ABC
```

It processes the event.

Later Kafka delivers the same event again:

```text
DeploymentRequested
eventId = ABC
```

The consumer detects that event `ABC` has already been processed and does not perform the business operation again.

---

## 6. Event Metadata

Forge events contain common metadata:

```json
{
  "eventId": "uuid",
  "eventType": "BuildCompleted",
  "schemaVersion": 1,
  "occurredAt": "timestamp",
  "producer": "build-service",
  "organizationId": "uuid",
  "projectId": "uuid",
  "requestId": "uuid",
  "traceId": "trace-id"
}
```

---

## 7. Why Kafka?

Kafka provides:

- durable event storage
- consumer groups
- replayability
- asynchronous communication
- independent consumers
- horizontal consumer scaling
- consumer offset management

These characteristics are useful for Forge's build, deployment, audit, and future analytics workflows.

---

## 8. Why Not REST Everywhere?

REST remains appropriate when an immediate response is required.

For example:

```text
GET project
Create project
Get deployment
Update project settings
```

Kafka is appropriate when an event represents something that happened and other services may react independently.

---

## 9. Consequences

### Positive

- Services become less tightly coupled.
- Long-running workflows become asynchronous.
- Consumers can scale independently.
- Events can be replayed where appropriate.
- New consumers can be added without changing the producer's core workflow.
- Kafka consumer failures can recover from offsets.

### Negative

- Eventual consistency.
- Duplicate delivery.
- Consumer lag.
- Schema evolution complexity.
- Kafka becomes additional infrastructure.
- Debugging distributed workflows is harder.

These costs are accepted.

---

## 10. Kafka Failure

If Kafka becomes temporarily unavailable:

```text
Service
   ↓
PostgreSQL
   ↓
Outbox
```

business state should remain committed.

The event remains in the outbox until it can be published.

Therefore a Kafka outage should not silently destroy a committed business operation.

---

## 11. Result

Kafka is adopted because Forge has genuine asynchronous workflows requiring durable event delivery and independent consumers.

Kafka is not introduced merely because it is commonly associated with production systems.