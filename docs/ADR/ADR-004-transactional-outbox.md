# ADR-004 — Transactional Outbox Pattern

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Use the transactional outbox pattern for reliable event publication

---

## 1. Context

Forge services frequently need to:

1. change business state
2. publish an event describing that change

Consider:

```text
BEGIN
Update deployment
COMMIT

Publish DeploymentSucceeded
```

The database update and Kafka publication are separate systems.

A process crash between them can create inconsistency.

---

## 2. Failure Scenario

Suppose:

```text
Deployment status = SUCCESS
```

is successfully committed to PostgreSQL.

The process then crashes before publishing:

```text
DeploymentSucceeded
```

Kafka never receives the event.

Another service may therefore never learn about the successful deployment.

---

## 3. Decision

Forge uses a transactional outbox.

The service performs:

```text
BEGIN TRANSACTION

Update business state

Insert event into outbox

COMMIT
```

Both operations occur in the same PostgreSQL transaction.

---

## 4. Example

When a build completes:

```text
Build status
     │
     ▼
SUCCESS
     │
     ├───────────────┐
     │               │
     ▼               ▼
builds table      outbox table
```

The transaction commits both.

Only afterward does the outbox publisher send the event to Kafka.

---

## 5. Outbox Publisher

A background publisher continuously processes unpublished records.

Conceptually:

```text
PostgreSQL
     │
     ▼
Outbox
     │
     ▼
Publisher
     │
     ▼
Kafka
```

After successful publication, the outbox record can be marked as published.

---

## 6. Publisher Crash

Suppose:

```text
Publish event
      ↓
Kafka accepts event
      ↓
Publisher crashes
      ↓
Database still says "unpublished"
```

The publisher may publish the event again.

Therefore the outbox pattern does **not** eliminate duplicates.

Instead:

> Reliable publication requires idempotent consumers.

---

## 7. Event Identity

Every event receives a unique:

```text
eventId
```

Consumers use this identifier for deduplication.

Example:

```text
eventId = 123
```

First delivery:

```text
123 → process
```

Second delivery:

```text
123 → already processed → ignore
```

---

## 8. Consequences

### Positive

- Business state and event creation are atomic.
- Events are not silently lost after a successful transaction.
- Kafka outages can be tolerated temporarily.
- Publication can be retried.
- Service restarts do not necessarily lose events.

### Negative

- Additional database table.
- Background publisher required.
- Duplicate publication is still possible.
- Consumers must implement idempotency.
- Outbox cleanup/retention is required.

---

## 9. Why Not Two-Phase Commit?

Distributed transactions involving PostgreSQL and Kafka would add substantial complexity and coupling.

Forge instead prefers:

```text
Local ACID transaction
+
Durable outbox
+
At-least-once delivery
+
Idempotent processing
```

This keeps service boundaries explicit.

---

## 10. Result

Every important domain event that must reliably correspond to a committed business-state change should use the transactional outbox pattern.

The outbox therefore becomes a core reliability mechanism of Forge.