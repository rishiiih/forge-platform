# ADR-003 — PostgreSQL as Authoritative Business State

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** PostgreSQL is the authoritative source of Forge business state

---

## 1. Context

Forge is a distributed system.

Multiple services communicate through APIs and Kafka events.

Distributed systems create a fundamental question:

> Which system contains the authoritative state?

For example, a deployment may exist simultaneously as:

- a PostgreSQL record
- a Kafka event
- a Docker container
- a reverse-proxy route

These representations must not become competing sources of truth.

---

## 2. Decision

PostgreSQL is the authoritative source for Forge's business state.

Services persist their own domain state in PostgreSQL.

Examples:

```text
Identity Service
    users
    sessions
    memberships

Control Plane
    projects
    environments
    repositories

Build Service
    builds
    artifacts

Deployment Service
    deployments
    deployment attempts

Runtime Service
    runtime instances
    routes
```

---

## 3. Service Data Ownership

Each service owns its data.

For example:

```text
Deployment Service
        │
        └── deployments
```

Other services must not directly modify that table.

If another service needs something to change, it should use:

- an API
- a command/event
- an explicitly defined integration contract

---

## 4. Initial Physical Database

Initially, Forge may run one PostgreSQL cluster.

Logical ownership remains separated through schemas or clearly defined table ownership.

Example:

```text
forge_identity
forge_control
forge_build
forge_deployment
forge_runtime
```

Physical database separation can be introduced later if operational requirements justify it.

---

## 5. Why PostgreSQL?

PostgreSQL provides:

- ACID transactions
- strong consistency
- relational constraints
- indexing
- mature tooling
- reliable persistence
- transaction support for business state and outbox records

Forge's core entities are highly relational, making a relational database appropriate.

---

## 6. PostgreSQL vs Kafka

The responsibilities are deliberately different.

### PostgreSQL

Stores:

```text
Current state
Historical records
Configuration
Relationships
Authorization data
Deployment state
```

### Kafka

Transports:

```text
Events
Notifications
Asynchronous workflow messages
```

Kafka does not replace PostgreSQL.

---

## 7. PostgreSQL vs Runtime

Docker is also not authoritative for business state.

For example, Docker may report:

```text
container running
```

but the Deployment Service determines the business state of the deployment.

Runtime state and business state are related but distinct.

---

## 8. No Distributed Transactions

Forge does not attempt to execute transactions spanning:

```text
PostgreSQL
+
Kafka
+
Docker
+
Multiple services
```

Instead it uses:

```text
Local transaction
+
Transactional outbox
+
Idempotent consumers
+
Compensating actions
```

---

## 9. Consequences

### Positive

- Clear source of truth.
- Strong relational consistency within service boundaries.
- Easier auditing.
- Predictable state transitions.
- Kafka can be treated as transport rather than permanent business truth.

### Negative

- Cross-service queries require APIs/events.
- Eventual consistency exists between services.
- Some workflows require reconciliation.
- Database scaling eventually becomes an architectural concern.

---

## 10. Result

PostgreSQL is the authoritative business-state store.

Kafka, Docker, caches, and derived observability systems must not silently become competing sources of truth.