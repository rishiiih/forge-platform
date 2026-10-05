# ADR-001 — Microservices Architecture

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Use purposeful microservices for Forge

---

## 1. Context

Forge is a production-oriented container deployment platform.

The platform contains several responsibilities with substantially different characteristics:

- authentication and authorization
- organization and project management
- source-control integration
- untrusted Docker builds
- deployment orchestration
- runtime/container management
- asynchronous event processing
- observability

These responsibilities have different:

- security boundaries
- failure modes
- scaling requirements
- deployment lifecycles
- data ownership
- operational characteristics

A single modular monolith would be simpler to develop and deploy, but it would make some of the distributed-system properties Forge is intended to demonstrate less meaningful.

In particular, build execution is fundamentally different from deployment orchestration.

Builds execute potentially hostile customer code, while deployment controls production workloads.

---

## 2. Decision

Forge will use a **purposeful microservices architecture**.

The initial core services are:

```text
Identity Service
Control Plane Service
Build Service
Deployment Service
Runtime Service
```

Supporting infrastructure includes:

```text
API Gateway
Kafka
PostgreSQL
Build Workers
OCI Registry
Docker Runtime
Observability Stack
```

The services will have explicit ownership boundaries.

---

## 3. Responsibilities

### Identity Service

Owns:

- users
- authentication
- sessions
- organization membership
- roles
- credentials

### Control Plane Service

Owns:

- projects
- environments
- repository configuration
- GitHub integration
- project configuration
- configuration snapshots

### Build Service

Owns:

- builds
- build state
- build metadata
- artifact metadata

### Deployment Service

Owns:

- deployments
- deployment attempts
- deployment state transitions
- rollback operations
- deployment orchestration

### Runtime Service

Owns:

- runtime instances
- containers
- routes
- runtime health
- runtime lifecycle

---

## 4. Why Not a Monolith?

A modular monolith would provide:

- simpler local development
- simpler deployment
- simpler transactions
- lower operational overhead

However, Forge intentionally needs to demonstrate:

- service isolation
- asynchronous communication
- independent failure
- independent deployment
- distributed tracing
- event-driven workflows
- data ownership
- idempotency

A microservice architecture provides meaningful boundaries for these concerns.

---

## 5. Why Not More Microservices?

Forge will not split every domain object into a separate service.

For example:

```text
Project Service
Environment Service
Repository Service
Deployment Service
```

would create unnecessary distributed communication.

Instead, related responsibilities remain within the Control Plane.

The goal is **bounded services**, not maximum service count.

---

## 6. Consequences

### Positive

- Build execution can be isolated from production control logic.
- Services can fail independently.
- Services can scale independently.
- Service ownership becomes explicit.
- Distributed-system failure scenarios become testable.
- The architecture can evolve toward multiple runtime hosts.
- Each service can have its own deployment lifecycle.

### Negative

- More infrastructure.
- More debugging complexity.
- Network failures become possible.
- Eventual consistency must be handled.
- Distributed tracing becomes necessary.
- Local development becomes more complicated.
- Database transactions cannot simply span all services.

These costs are accepted because they are part of Forge's engineering objectives.

---

## 7. Constraints

Microservices must not be introduced solely for appearance.

A new service requires a meaningful justification such as:

- security isolation
- independent scaling
- independent lifecycle
- separate data ownership
- separate failure domain

---

## 8. Future Evolution

The initial architecture may evolve toward:

```text
Microservices
      ↓
Multiple service instances
      ↓
Multiple runtime hosts
      ↓
Runtime scheduler
      ↓
Autoscaling
      ↓
Kubernetes
```

The evolution should be driven by measured requirements rather than assumptions.

---

## 9. Result

Forge uses a small number of meaningful microservices rather than either:

- a monolithic architecture, or
- an unnecessarily fragmented microservice architecture.