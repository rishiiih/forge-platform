# ADR-006 — Immutable OCI Artifacts

**Status:** Accepted  
**Date:** 2026-10-05  
**Decision:** Deployments reference immutable OCI image digests rather than mutable tags

---

## 1. Context

Forge builds customer repositories into container images.

Container registries commonly support mutable tags:

```text
my-app:latest
my-app:production
my-app:v1
```

A tag can potentially point to different image contents over time.

That creates a reproducibility problem.

For example:

```text
Deployment 101
image = my-app:latest
```

might run image A today.

Later:

```text
my-app:latest
```

may point to image B.

The deployment record no longer uniquely identifies what was deployed.

---

## 2. Decision

Forge uses immutable OCI image digests as deployment artifact identities.

Example:

```text
sha256:8f2c9e...
```

A deployment stores:

```text
artifactDigest = sha256:8f2c9e...
```

The digest is authoritative.

---

## 3. Build Flow

```text
Git Commit
    │
    ▼
Build Worker
    │
    ▼
BuildKit
    │
    ▼
OCI Image
    │
    ▼
Registry
    │
    ▼
Image Digest
    │
    ▼
Build Record
```

---

## 4. Deployment Flow

The Deployment Service receives:

```text
artifactDigest = sha256:ABC
```

It does not resolve:

```text
latest
```

at deployment time.

Instead:

```text
Deployment
    │
    └── sha256:ABC
```

uniquely identifies the artifact.

---

## 5. Reproducibility

Suppose:

```text
Build 42
commit = a1b2c3
digest = sha256:ABC
```

produces a production deployment.

Later:

```text
Rollback
```

should reference:

```text
sha256:ABC
```

not:

```text
latest
```

This makes rollback deterministic.

---

## 6. Deployment Immutability

After a deployment is created, these fields cannot change:

```text
commit SHA
artifact digest
environment
configuration snapshot
```

For example:

```text
Deployment 104
├── commit = a1b2c3
├── artifact = sha256:ABC
└── environment = production
```

cannot later become:

```text
artifact = sha256:XYZ
```

---

## 7. Tags

Tags may still be useful for human convenience.

For example:

```text
forge/project:build-42
forge/project:a1b2c3
```

But tags are not deployment authority.

The digest remains authoritative.

---

## 8. Artifact Retention

An artifact must not be deleted while it is:

- currently live
- referenced by a retained rollback
- required by an active deployment

Cleanup must consider deployment references.

---

## 9. Security Benefits

Immutable artifacts reduce the risk of unexpected changes between:

```text
Build
    ↓
Deployment
```

The exact artifact produced by the build is the artifact deployed.

---

## 10. Rollback

Rollback is implemented as a new deployment.

Example:

```text
Deployment 100
digest = A

Deployment 101
digest = B

Deployment 102
digest = A
```

Deployment 102 is a new immutable record.

Deployment 100 remains part of history.

---

## 11. Consequences

### Positive

- Deterministic deployments.
- Reliable rollback.
- Reproducibility.
- Better auditability.
- Easier incident investigation.
- Clear artifact lineage.

### Negative

- Artifact cleanup becomes more complicated.
- Registry storage grows over time.
- Deployment records must maintain artifact references.

These trade-offs are accepted.

---

## 12. Result

Forge treats the OCI digest as the canonical identity of a deployable artifact.

Mutable tags may exist for convenience but never determine what production runs.