# ADR-007 — Safe Deployment Strategy

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Forge will use start → health-check → traffic-switch deployment

---

## 1. Context

A deployment platform must avoid replacing a healthy production workload with an unhealthy version.

A naive deployment could perform:

```text
Stop old container
      ↓
Start new container
```

If the new container fails to start, production becomes unavailable.

Forge therefore requires a deployment strategy that preserves the currently healthy version until the new version is proven healthy.

---

## 2. Decision

Forge uses:

```text
Start New
     ↓
Health Check
     ↓
Switch Traffic
```

The currently live deployment remains active until the new deployment passes its health checks.

---

## 3. Deployment Flow

```text
Current Version
      │
      │ still serving traffic
      ▼
Start New Container
      │
      ▼
Startup Grace Period
      │
      ▼
Health Checks
      │
      ├─────────────── FAIL
      │                    │
      │                    ▼
      │              Remove New Container
      │                    │
      │                    ▼
      │              Keep Old Version
      │
      ▼
     PASS
      │
      ▼
Switch Reverse Proxy
      │
      ▼
New Version Live
```

---

## 4. Health Check Requirements

A project may configure:

```text
Path
Port
Timeout
Interval
Retries
Initial grace period
Success threshold
```

The Deployment Service determines whether the deployment is healthy according to the configured policy.

---

## 5. Old Version Protection

Suppose:

```text
Production
    ↓
Version A
```

is currently healthy.

A deployment of Version B begins.

If B fails:

```text
Production
    ↓
Version A
```

remains active.

Version B is removed.

---

## 6. No Health Check Success, No Traffic Switch

The following invariant applies:

```text
Deployment cannot become live
unless its health-check policy succeeds.
```

This is a core Forge reliability guarantee.

---

## 7. Deployment Concurrency

For each environment:

```text
At most one in-flight deployment
```

may modify production state.

This prevents competing deployments from simultaneously changing the runtime.

---

## 8. Automatic Deployment Ordering

For automatic deployments:

```text
Commit A
Commit B
Commit C
```

If A is queued but has not started and C becomes the newest desired deployment, older queued automatic deployments may be superseded.

The latest automatic deployment wins before execution begins.

Manual deployments and explicit rollbacks must not be silently discarded.

---

## 9. Cancellation

Cancellation is supported before the irreversible traffic switch.

```text
QUEUED
   ↓
DEPLOYING
   ↓
HEALTH_CHECKING
   ↓
TRAFFIC SWITCH
```

The first three phases may be cancellable.

After traffic switching, rollback is the appropriate recovery operation.

---

## 10. Failure Cases

### Container fails to start

```text
Deployment → FAILED
Old version → remains live
```

### Health check fails

```text
Deployment → FAILED
New container → removed
Old version → remains live
```

### Deployment times out

```text
Deployment → TIMEOUT
New workload → cleaned up where safe
Old version → remains live
```

---

## 11. Rollback

Rollback creates a new deployment.

Example:

```text
A → B
```

If B is unhealthy:

```text
Rollback
   ↓
New deployment of A
```

The system does not mutate the old deployment record.

---

## 12. Why Not Blue/Green?

Blue/green deployment is intentionally deferred.

The initial Docker runtime can provide the important safety property without requiring a full blue/green orchestration model.

Forge can evolve later toward:

```text
Safe replacement
      ↓
Blue/Green
      ↓
Canary
```

when real requirements justify it.

---

## 13. Consequences

### Positive

- Healthy versions are protected.
- Failed deployments are isolated.
- Rollbacks are straightforward.
- Deployment behavior is deterministic.
- The strategy is implementable on a single Docker host.

### Negative

- Additional runtime capacity may be temporarily required.
- Health-check configuration becomes important.
- Proxy switching must be reliable.
- Some deployment edge cases require reconciliation.

These costs are accepted.

---

## 14. Result

Forge uses a safe deployment strategy based on:

```text
Start
+
Health Check
+
Traffic Switch
```

rather than stopping the existing production version first.