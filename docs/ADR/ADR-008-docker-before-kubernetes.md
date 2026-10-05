# ADR-008 — Docker Runtime Before Kubernetes

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Forge will initially use Docker as its runtime and defer Kubernetes

---

## 1. Context

Forge is a container deployment platform.

Kubernetes is a powerful platform for container orchestration, but adopting it immediately would introduce a large amount of infrastructure unrelated to Forge's core learning objectives.

Kubernetes introduces concepts such as:

- cluster control plane
- scheduler
- controllers
- services
- ingress
- networking
- cluster RBAC
- storage
- node management
- reconciliation

Forge should first implement these platform concepts itself where appropriate.

---

## 2. Decision

The initial Forge runtime will use:

```text
Runtime Service
      ↓
Docker
      ↓
Container
      ↓
Reverse Proxy
```

The initial environment may use a single Docker runtime host.

---

## 3. Why Docker?

Docker provides enough functionality for Forge's initial requirements:

- image execution
- container lifecycle
- port mappings
- resource controls
- logs
- health checks
- container isolation
- container inspection

This allows Forge to focus on building the platform layer around the runtime.

---

## 4. What Forge Builds Itself

Forge will implement concepts such as:

```text
Deployment state machine
Health-check orchestration
Deployment concurrency
Artifact selection
Rollback
Runtime lifecycle
Traffic switching
Deployment history
Failure recovery
```

These are important parts of the platform's engineering challenge.

---

## 5. Runtime Architecture

Initial:

```text
                    Runtime Service
                          │
            ┌─────────────┼─────────────┐
            │             │             │
            ▼             ▼             ▼
        Container A   Container B   Container C
            │             │             │
            └─────────────┼─────────────┘
                          │
                          ▼
                    Reverse Proxy
```

---

## 6. Single Host

The first implementation may run on one host:

```text
Docker Host
├── Forge services
├── Customer container A
├── Customer container B
└── Customer container C
```

This is intentionally limited.

It allows the system to prove its deployment and runtime behavior before introducing distributed scheduling.

---

## 7. Why Not Kubernetes Initially?

Starting with Kubernetes could hide some of the engineering Forge is supposed to demonstrate.

For example, instead of designing:

```text
Deployment
    ↓
Runtime Service
    ↓
Start container
    ↓
Health check
    ↓
Switch traffic
```

Forge might simply ask Kubernetes to create a Deployment.

That would reduce the amount of platform behavior implemented and understood directly.

---

## 8. Future Evolution

The runtime can evolve as follows:

```text
Stage 1
Docker / Single Host
        ↓
Stage 2
Multiple Docker Hosts
        ↓
Stage 3
Runtime Scheduler
        ↓
Stage 4
Capacity-aware Scheduling
        ↓
Stage 5
Autoscaling
        ↓
Stage 6
Kubernetes Runtime
```

---

## 9. Migration Boundary

The Runtime Service should hide the underlying runtime implementation.

Conceptually:

```text
Deployment Service
        │
        ▼
Runtime Interface
        │
        ├────────► Docker Runtime
        │
        └────────► Kubernetes Runtime (future)
```

The Deployment Service should not contain Docker-specific business logic everywhere.

---

## 10. Consequences

### Positive

- Smaller initial infrastructure.
- Easier local development.
- Easier debugging.
- Greater control over runtime behavior.
- More direct demonstration of deployment engineering.
- Clear path toward Kubernetes later.

### Negative

- Limited initial scheduling capabilities.
- Limited fault tolerance.
- Limited horizontal capacity.
- Runtime management must be implemented by Forge.

These limitations are intentional for the initial architecture.

---

## 11. Kubernetes Adoption Criteria

Kubernetes should be considered when Forge demonstrates a real need for:

- multiple runtime hosts
- scheduling
- workload rescheduling
- autoscaling
- higher runtime availability
- cluster-level resource management

The decision should be backed by measured requirements rather than the desire to add Kubernetes to the technology list.

---

## 12. Result

Forge starts with Docker because it provides the smallest runtime capable of supporting the platform's deployment model.

Kubernetes remains a future runtime implementation rather than an unnecessary initial dependency.