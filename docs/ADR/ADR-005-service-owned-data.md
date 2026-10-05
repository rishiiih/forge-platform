# ADR-005 — Service-Owned Data

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Each Forge service owns and controls its domain data

---

## 1. Context

Forge uses multiple services that communicate through APIs and Kafka.

A distributed architecture becomes difficult to reason about if every service can directly access and modify every other service's database tables.

For example, allowing the Deployment Service to directly modify Build Service tables would create hidden coupling:

```text
Deployment Service
       │
       └──── direct SQL ────► Build tables
```

This makes service boundaries largely meaningless.

---

## 2. Decision

Each service owns its domain data.

```text
Identity Service
    └── Identity data

Control Plane
    └── Project/configuration data

Build Service
    └── Build/artifact data

Deployment Service
    └── Deployment data

Runtime Service
    └── Runtime data
```

A service may read another service's information only through an explicit API or event contract.

---

## 3. Initial Database Topology

Forge initially uses one PostgreSQL cluster.

Logical ownership is still separated.

Example:

```text
PostgreSQL
│
├── forge_identity
├── forge_control
├── forge_build
├── forge_deployment
└── forge_runtime
```

The fact that these schemas exist in the same PostgreSQL instance does **not** mean services may freely access one another's tables.

---

## 4. Ownership Rules

The owning service is responsible for:

- schema definition
- migrations
- validation
- business rules
- writes
- lifecycle
- data retention

For example:

```text
Deployment Service
        │
        ▼
deployment database
```

Only the Deployment Service should directly mutate deployment state.

---

## 5. Cross-Service Data

Suppose Deployment Service needs the artifact digest produced by Build Service.

It should receive:

```text
BuildCompleted
```

containing:

```json
{
  "buildId": "...",
  "artifactDigest": "sha256:..."
}
```

The Deployment Service can then persist the information it needs in its own database.

It should not execute:

```sql
SELECT artifact_digest
FROM build_service.builds
WHERE id = ?;
```

---

## 6. Data Duplication

Some information may intentionally exist in more than one service.

For example:

```text
Build Service
    └── artifactDigest

Deployment Service
    └── artifactDigest used by deployment
```

This is acceptable.

The important distinction is:

> Duplicated information is acceptable; shared ownership is not.

---

## 7. Why This Boundary Matters

Service-owned data provides:

- clear ownership
- independent schema evolution
- reduced coupling
- safer deployments
- easier service extraction
- clearer failure boundaries

It also prevents a common anti-pattern where multiple "microservices" are effectively just different HTTP controllers around one shared database.

---

## 8. Database Foreign Keys

Cross-service database foreign keys should generally not be used.

For example:

```text
deployment.project_id
```

may contain the project identifier owned by Control Plane.

But PostgreSQL should not require a foreign key into the Control Plane schema.

The Deployment Service validates the relationship through an API/event contract.

---

## 9. Consequences

### Positive

- Strong service boundaries.
- Independent schema migrations.
- Reduced coupling.
- Clear ownership.
- Better long-term scalability.

### Negative

- Cross-service joins are unavailable.
- Some information is duplicated.
- Eventual consistency is introduced.
- Queries spanning domains require APIs or materialized views.

These trade-offs are accepted.

---

## 10. Future Evolution

Initially:

```text
One PostgreSQL Cluster
      ↓
Logical Service Ownership
```

Later, if justified:

```text
Identity DB
Control DB
Build DB
Deployment DB
Runtime DB
```

Physical database separation is therefore an optimization/evolution, not a prerequisite for service ownership.

---

## 11. Result

Forge treats service-owned data as a fundamental microservice boundary.

No service may bypass another service's ownership rules through direct database access.