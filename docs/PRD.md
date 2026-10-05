# Forge — Product Requirements Document

**Product:** Forge — Production-Grade Developer Platform  
**Document Version:** 1.0  
**Status:** Product Requirements Baseline  
**Target Users:** Developers, engineering teams, platform engineers  
**Primary Backend:** Java 21 + Spring Boot  
**Frontend:** React + TypeScript + Vite  
**Database:** PostgreSQL  
**Event Backbone:** Apache Kafka  
**Build Infrastructure:** Docker + BuildKit  
**Runtime:** Docker  
**Artifact Format:** OCI images  
**Observability:** OpenTelemetry + Prometheus + Grafana

---

# 1. Product Overview

Forge is a developer platform that automates the journey from source code to a running production application.

The core product loop is:

```text
Developer
    ↓
Organization
    ↓
Project
    ↓
Repository
    ↓
Build
    ↓
Immutable Artifact
    ↓
Deployment
    ↓
Health Check
    ↓
Production Runtime
    ↓
Logs / Metrics / Traces
    ↓
Incident
    ↓
Rollback / Resolution
```

Forge is designed as a **production-oriented distributed system**, not simply as a CRUD dashboard around Docker.

The product demonstrates real software-engineering concerns including:

- authentication;
- multi-tenancy;
- RBAC;
- asynchronous processing;
- event-driven architecture;
- immutable artifacts;
- secure build execution;
- deployment state machines;
- health checks;
- rollback;
- idempotency;
- failure recovery;
- distributed tracing;
- auditability;
- rate limiting;
- operational observability.

---

# 2. Product Vision

Forge aims to provide developers with a simple deployment experience while internally demonstrating the engineering principles required to operate a reliable developer platform.

The desired developer experience is:

```text
Connect GitHub
      ↓
Create Project
      ↓
Configure Environment
      ↓
Push Code
      ↓
Forge Builds
      ↓
Forge Deploys
      ↓
Application Goes Live
```

The complexity should be handled by Forge rather than exposed to the developer.

---

# 3. Problem Statement

Developers commonly need to combine:

- Git hosting;
- build infrastructure;
- containerization;
- deployment automation;
- runtime management;
- logging;
- monitoring;
- rollback;
- secrets;
- access control.

Forge brings these workflows into one platform.

The challenge is not merely automating deployment.

Forge must demonstrate that deployment automation remains reliable when:

- services fail;
- events are duplicated;
- workers crash;
- builds fail;
- health checks fail;
- users retry requests;
- multiple organizations share the platform;
- infrastructure becomes temporarily unavailable.

---

# 4. Product Goals

## 4.1 Primary Goals

Forge must:

1. Provide GitHub-based application deployment.
2. Build repositories into immutable OCI images.
3. Deploy immutable artifacts to a Docker runtime.
4. Perform health checks before switching traffic.
5. Support rollback.
6. Provide deployment history.
7. Support organizations and RBAC.
8. Isolate customer builds from the control plane.
9. Process long-running workflows asynchronously.
10. Survive duplicate events and worker failures.
11. Provide distributed observability.
12. Preserve tenant isolation.
13. Provide auditable administrative actions.
14. Demonstrate measurable reliability through tests.

---

# 5. Non-Goals

The initial product will not attempt to become:

- Kubernetes;
- a full cloud provider;
- a Datadog replacement;
- a general-purpose CI platform;
- a managed database platform;
- a CDN;
- a service mesh;
- a multi-region deployment platform;
- a full-featured Git hosting platform.

The system should remain focused on:

> **Source → Build → Artifact → Deploy → Runtime**

---

# 6. Target Architecture

Forge uses a small number of purposeful microservices.

```text
                         React + TypeScript
                                │
                                ▼
                          API Gateway
                                │
        ┌───────────────────────┼────────────────────────┐
        │                       │                        │
        ▼                       ▼                        ▼
 Identity Service       Control Plane Service     Deployment Service
        │                       │                        │
        └──────────────┬────────┴──────────────┬─────────┘
                       │                       │
                       ▼                       ▼
                  PostgreSQL                 Kafka
                                               │
                           ┌───────────────────┼─────────────────┐
                           │                   │                 │
                           ▼                   ▼                 ▼
                     Build Service      Deployment Workers   Audit/Other
                           │
                           ▼
                     Build Workers
                           │
                           ▼
                        BuildKit
                           │
                           ▼
                     OCI Registry

Deployment Service
        │
        ▼
Runtime Service
        │
        ▼
Docker Runtime
        │
        ▼
Reverse Proxy
        │
        ▼
Customer Application
```

The architecture is intentionally distributed because different parts of Forge have different:

- security boundaries;
- workload characteristics;
- scaling requirements;
- failure modes;
- ownership boundaries.

---

# 7. Core Services

Forge initially contains five primary domain services.

| Service | Responsibility |
|---|---|
| Identity Service | Authentication, users, sessions, credentials, identity |
| Control Plane Service | Organizations, projects, environments, GitHub, configuration |
| Build Service | Build lifecycle and artifact creation |
| Deployment Service | Deployment orchestration, health checks, rollback |
| Runtime Service | Containers, routing, runtime health |

Supporting infrastructure includes:

- API Gateway;
- Kafka;
- PostgreSQL;
- Build Workers;
- OCI Registry;
- OpenTelemetry Collector;
- Prometheus;
- Grafana;
- reverse proxy.

---

# 8. Identity Requirements

## 8.1 User Registration

Users can create accounts.

The system must store:

- user ID;
- email;
- password hash;
- account status;
- creation timestamp;
- update timestamp.

Passwords must never be stored in plaintext.

---

## 8.2 Authentication

Forge must support secure authentication.

Authentication includes:

- login;
- logout;
- access-token issuance;
- refresh-token handling;
- credential revocation.

Revoked credentials must not authorize new requests.

---

## 8.3 Personal Organization

Every newly registered developer receives a personal organization.

There is no special authorization model for personal organizations.

The same organization and RBAC mechanisms are used for personal and team organizations.

---

# 9. Organizations

Organizations are the primary tenant boundary.

An organization owns:

- projects;
- environments;
- deployments;
- builds;
- configuration;
- secrets;
- audit history.

Every organization must always have at least one `OWNER`.

---

# 10. Organization RBAC

Initial roles:

```text
OWNER
ADMIN
DEVELOPER
VIEWER
```

Permissions must be explicitly defined.

Examples:

| Action | OWNER | ADMIN | DEVELOPER | VIEWER |
|---|---:|---:|---:|---:|
| View project | ✓ | ✓ | ✓ | ✓ |
| Modify project | ✓ | ✓ | ✓ | ✗ |
| Deploy | ✓ | ✓ | ✓ | ✗ |
| Rollback | ✓ | ✓ | ✓ | ✗ |
| Manage members | ✓ | ✓ | ✗ | ✗ |
| Manage organization | ✓ | ✓ | ✗ | ✗ |
| Delete organization | ✓ | ✗ | ✗ | ✗ |

Exact permission granularity may evolve.

---

# 11. Tenant Isolation

All organization-owned resources must be associated with an organization.

Authorization must validate:

```text
Authenticated User
        ↓
Organization Membership
        ↓
Required Permission
        ↓
Resource Ownership
```

An object ID alone is never sufficient authorization.

The system must explicitly prevent IDOR/cross-tenant access.

---

# 12. Projects

A project represents one deployable application.

A project contains:

- project identity;
- repository;
- branch configuration;
- Dockerfile path;
- build context;
- environment configuration;
- deployment configuration;
- health-check configuration;
- deployment history.

---

# 13. Environments

A project may contain environments such as:

```text
development
staging
production
```

Each environment has independent:

- configuration;
- secrets;
- deployment history;
- runtime state;
- health-check settings.

---

# 14. Configuration

Project configuration includes:

- GitHub repository;
- branch;
- Dockerfile path;
- build context;
- application port;
- environment variables;
- secrets;
- health-check configuration;
- deployment strategy;
- auto-deployment configuration.

---

# 15. Configuration Snapshots

A deployment must reference an immutable configuration snapshot.

Once a deployment is created:

```text
commit SHA
artifact digest
environment
configuration snapshot
```

must not change.

This prevents a deployment from changing meaning while it is executing.

---

# 16. GitHub Integration

Forge integrates with GitHub as the initial source-control provider.

The Control Plane manages:

- repository connection;
- branch configuration;
- webhook registration;
- webhook validation;
- GitHub metadata.

GitLab and Bitbucket are future integrations.

---

# 17. Webhooks

Forge receives GitHub webhooks.

For every webhook:

1. Verify signature.
2. Validate repository.
3. Validate event type.
4. Validate organization/project ownership.
5. Persist webhook delivery identity.
6. Prevent duplicate processing.
7. Produce the corresponding domain event.

Invalid webhook signatures must be rejected.

---

# 18. Webhook Idempotency

GitHub may retry webhook delivery.

Therefore:

```text
Webhook A
Webhook A again
Webhook A again
```

must not produce:

```text
Build A
Build A
Build A
```

The GitHub delivery ID is used for deduplication.

---

# 19. Build Requirements

A build converts:

```text
Git commit
```

into:

```text
Immutable OCI image
```

The repository must contain a valid Dockerfile.

Forge does not initially provide language-specific buildpacks.

---

# 20. Build Lifecycle

Build states:

```text
QUEUED
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELLED
```

Every build must eventually reach a terminal state.

No build may remain indefinitely in a non-terminal state.

---

# 21. Build Architecture

```text
GitHub
  ↓
Control Plane
  ↓
CommitPushed Event
  ↓
Kafka
  ↓
Build Service
  ↓
Build Worker
  ↓
BuildKit
  ↓
OCI Registry
```

The Build Service coordinates the workflow.

The Build Worker performs the actual untrusted build.

---

# 22. Build Security

Builds execute customer-controlled code.

Therefore build workers are treated as security boundaries.

Build workers must not have unrestricted access to:

- PostgreSQL;
- production services;
- Forge credentials;
- runtime secrets;
- host filesystem;
- Docker control plane;
- cloud metadata services.

The MVP may use Docker/BuildKit-based isolation.

This is explicitly considered a development-grade isolation mechanism rather than a hardened multi-tenant sandbox.

---

# 23. Build Secrets

Build-time secrets and runtime production secrets are separate.

Production secrets must not automatically be available to builds.

Fork/PR builds receive no production secrets.

Production deployment must require an authorized trusted flow.

---

# 24. Artifact Requirements

Every successful build produces an immutable OCI artifact.

The authoritative identity is:

```text
sha256:<digest>
```

Tags are not authoritative.

Forge must never deploy an artifact solely because it is tagged:

```text
latest
```

---

# 25. Artifact Retention

Artifacts must be retained while required for:

- current production deployment;
- rollback;
- deployment history;
- configured retention policy.

An artifact currently serving production traffic must never be deleted.

---

# 26. Deployment Requirements

Deployment converts:

```text
Immutable artifact
+
Immutable configuration
```

into:

```text
Running application
```

Deployment is a first-class state machine.

---

# 27. Deployment States

Initial states:

```text
QUEUED
DEPLOYING
HEALTH_CHECKING
SUCCESS
FAILED
TIMEOUT
CANCELLED
SKIPPED
```

Invalid state transitions must be rejected.

---

# 28. Deployment Invariants

A deployment must permanently retain:

- commit SHA;
- artifact digest;
- environment;
- configuration snapshot;
- initiating user/system;
- creation timestamp.

None of these may be mutated after creation.

---

# 29. Deployment Concurrency

At most one deployment may be in-flight for an environment.

Example:

```text
production

Deployment A → DEPLOYING
Deployment B → QUEUED
Deployment C → QUEUED
```

For automatic deployments, obsolete queued deployments may be marked `SKIPPED`.

Manual deployments and rollbacks must not be silently skipped.

---

# 30. Deployment Process

Forge uses a safe deployment strategy:

```text
Start new container
       ↓
Wait for startup
       ↓
Health checks
       ↓
Pass?
 ┌─────┴─────┐
 │           │
NO          YES
 │           │
 ▼           ▼
Remove     Switch
new        traffic
container    │
 │           ▼
 │         New live
 ▼
Old remains live
```

The currently healthy application remains live until the new version is validated.

---

# 31. Health Checks

Health checks are configurable.

Configuration includes:

- path;
- port;
- timeout;
- interval;
- retries;
- initial grace period;
- success threshold where applicable.

A deployment cannot become live without successful health verification.

---

# 32. Failed Deployment

If a new deployment fails:

```text
New container
     ↓
Health check failure
     ↓
New container removed
     ↓
Old deployment remains live
```

If there is no previous successful deployment:

```text
Environment = NO_HEALTHY_DEPLOYMENT
```

---

# 33. Rollback

Rollback creates a new deployment.

It does not mutate an existing deployment.

Example:

```text
Deployment 42
artifact = sha256:A
status = SUCCESS

Rollback

Deployment 57
artifact = sha256:A
trigger = ROLLBACK
status = QUEUED
```

Deployment 42 remains immutable.

---

# 34. Deployment Cancellation

A deployment may be cancelled before the irreversible traffic-switch point.

After traffic has been switched, the deployment is treated according to its actual resulting state.

Cancellation must not leave an environment in an ambiguous state.

---

# 35. Runtime

The Runtime Service manages customer applications.

Responsibilities:

- container lifecycle;
- runtime health;
- resource configuration;
- routing;
- reverse-proxy integration;
- container logs;
- basic request metrics.

---

# 36. Runtime/Data-Plane Independence

Customer applications must not depend on the Forge Control Plane for every request.

Therefore:

```text
Customer
   ↓
Reverse Proxy
   ↓
Customer Container
```

continues functioning even if:

```text
Control Plane
```

is temporarily unavailable.

---

# 37. Initial Runtime

The MVP uses:

```text
One Docker runtime host
+
Reverse proxy
```

This provides a manageable initial implementation.

Future versions may introduce:

```text
Multiple hosts
      ↓
Scheduler
      ↓
Kubernetes
```

when justified.

---

# 38. Customer Logs

Forge initially provides:

- container stdout;
- container stderr.

Customer log retention target:

**7 days**

Logs should be associated with:

- organization;
- project;
- environment;
- deployment;
- container;
- timestamp.

---

# 39. Customer Metrics

The MVP provides basic request metrics:

- request count;
- status code;
- latency;
- error rate.

Forge does not initially implement full arbitrary customer metric ingestion.

---

# 40. Customer Tracing

Customer distributed tracing is explicitly outside the MVP.

Forge itself, however, must have distributed tracing.

This distinction is intentional.

---

# 41. Kafka Event Architecture

Kafka is the event backbone for asynchronous Forge workflows.

Kafka transports events.

PostgreSQL remains authoritative for business state.

Example:

```text
Control Plane
      ↓
CommitPushed
      ↓
Kafka
      ↓
Build Service
```

---

# 42. Initial Event Types

Initial events include:

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

Events are versioned.

---

# 43. Event Schema

Events contain common metadata:

```json
{
  "eventId": "...",
  "eventType": "...",
  "schemaVersion": 1,
  "occurredAt": "...",
  "producer": "...",
  "organizationId": "...",
  "projectId": "...",
  "requestId": "...",
  "traceId": "..."
}
```

Event payloads are domain-specific.

---

# 44. Event Delivery

Forge assumes at-least-once event delivery.

Therefore consumers must be idempotent.

An event may be processed more than once without producing duplicate business effects.

---

# 45. Transactional Outbox

Whenever a database transaction must result in an event:

```text
BEGIN
  update business state
  insert outbox event
COMMIT
```

A separate publisher sends outbox events to Kafka.

This prevents:

```text
Database commit succeeds
Kafka publish fails
```

from silently losing the event.

---

# 46. Service Data Ownership

Services own their business data.

A service must not directly mutate another service's database tables.

Initial logical ownership:

```text
Identity
  → users, sessions, credentials

Control Plane
  → organizations, memberships, projects, environments

Build
  → builds, artifacts, build metadata

Deployment
  → deployments, deployment attempts, state transitions

Runtime
  → runtime instances, routes, health state
```

A single PostgreSQL cluster may initially host these logical data boundaries.

---

# 47. Database Evolution

Initial deployment:

```text
One PostgreSQL cluster
+
Separate service-owned schemas
```

Future evolution may introduce separate databases if required by:

- scaling;
- security;
- operational isolation;
- availability;
- independent lifecycle.

Physical database separation is not required merely for architectural appearance.

---

# 48. Distributed Transactions

Forge does not use distributed ACID transactions.

Cross-service workflows use:

- local transactions;
- events;
- state machines;
- idempotency;
- retries;
- compensating actions where necessary.

---

# 49. API Gateway

The API Gateway provides:

- TLS termination;
- routing;
- request IDs;
- CORS;
- rate limiting;
- request-size limits;
- external API versioning.

The Gateway does not contain business authorization logic.

Authorization remains with the service that owns the resource.

---

# 50. Rate Limiting

Rate limits are endpoint-specific.

Examples:

- login;
- webhook ingestion;
- deployment creation;
- general API requests.

Responses use:

```text
HTTP 429
Retry-After
```

Security-sensitive operations should fail closed when their required rate-limiting mechanism is unavailable.

---

# 51. Observability

Forge must be observable as a distributed system.

Every service emits:

- structured logs;
- metrics;
- traces.

Technology:

```text
OpenTelemetry
Prometheus
Grafana
OpenTelemetry Collector
```

---

# 52. Distributed Tracing

A deployment should be traceable across:

```text
API Gateway
    ↓
Deployment Service
    ↓
Kafka
    ↓
Runtime Service
    ↓
Docker
```

Trace context must be propagated across asynchronous Kafka boundaries.

---

# 53. Request Correlation

Every external request receives a request ID.

Example:

```text
X-Request-ID
```

The ID should appear in:

- service logs;
- relevant events;
- audit records;
- deployment operations.

---

# 54. Forge Metrics

Forge itself must expose metrics such as:

### API

- request count;
- latency;
- error rate.

### Kafka

- consumer lag;
- processing failures;
- retry counts.

### Builds

- queue time;
- duration;
- success/failure;
- worker utilization.

### Deployments

- duration;
- failure rate;
- health-check duration;
- rollback count.

### Runtime

- container restarts;
- CPU;
- memory;
- request rate;
- HTTP errors.

---

# 55. Alerting

Initial alert examples include:

```text
5xx rate > 5%
for 5 consecutive minutes
```

Alerts should be deduplicated.

One active condition should produce one active incident.

---

# 56. Incidents

Incident states:

```text
OPEN
ACKNOWLEDGED
MITIGATED
RESOLVED
```

Incident records contain:

- organization;
- project;
- environment;
- condition;
- start time;
- detection time;
- related deployment;
- timeline events.

---

# 57. Audit Logging

Forge maintains an append-only audit history.

Audited actions include:

- login;
- organization changes;
- membership changes;
- role changes;
- project creation;
- project deletion;
- secret operations;
- build operations;
- deployments;
- rollbacks;
- configuration changes.

Audit logs have a target retention of:

**1 year**

---

# 58. Project Deletion

Deletion is an explicit lifecycle.

Suggested state progression:

```text
ACTIVE
   ↓
DELETION_REQUESTED
   ↓
STOPPING
   ↓
STOPPED
   ↓
DELETED
```

Active workloads must be stopped before project deletion completes.

---

# 59. Secrets

Secret values must never be returned by normal APIs.

Supported operations:

```text
CREATE
ROTATE
DELETE
```

Secret metadata may be returned.

Secret values must be encrypted at rest.

---

# 60. Authentication and Authorization Security

Security requirements include:

- password hashing;
- secure token handling;
- credential revocation;
- RBAC;
- tenant isolation;
- IDOR prevention;
- webhook signature validation;
- secret protection;
- audit logging;
- rate limiting.

---

# 61. Service Failure Requirements

A service failure must have a defined behavior.

### Identity failure

New authentication operations may fail.

Already-running customer applications continue operating.

### Control Plane failure

Configuration and management operations may fail.

Running applications continue serving traffic.

### Build Service failure

Build requests remain recoverable through Kafka/state.

### Deployment Service failure

Deployment state remains recoverable.

### Runtime Service failure

Affected runtime management operations may fail.

Existing containers should continue serving traffic where possible.

---

# 62. Kafka Failure Requirements

If Kafka becomes temporarily unavailable:

- customer applications must continue serving traffic;
- already-running containers remain unaffected;
- asynchronous operations may be delayed;
- producers retry according to policy;
- persisted state must not be silently lost.

---

# 63. Worker Failure Recovery

A build worker may crash while processing a build.

The build system must detect stale work using:

- leases;
- heartbeat;
- timeout;
- persisted build state.

A new worker can recover the build.

---

# 64. Idempotency Requirements

The following operations require explicit idempotency:

- GitHub webhook processing;
- build creation;
- deployment creation;
- Kafka event consumption;
- rollback;
- runtime lifecycle operations where repeated commands could be harmful.

---

# 65. Security of Untrusted Runtime Applications

Customer containers must not automatically receive access to:

- Forge credentials;
- internal control-plane APIs;
- PostgreSQL;
- Kafka;
- build infrastructure.

Runtime networking should follow least privilege.

---

# 66. Frontend

Forge's frontend uses:

```text
React
TypeScript
Vite
React Router
TanStack Query
Tailwind CSS
```

Zustand may be introduced where client-side state requires it.

The frontend must not contain authoritative authorization logic.

---

# 67. Frontend Areas

Initial UI areas:

```text
Authentication
Organizations
Projects
Project Settings
Repository
Builds
Deployments
Deployment Details
Runtime
Logs
Environment Variables
Secrets
Members / RBAC
Incidents
Audit Log
```

---

# 68. Deployment Dashboard

The deployment interface should expose:

- deployment status;
- commit SHA;
- artifact digest;
- environment;
- trigger;
- initiator;
- timestamps;
- health-check status;
- logs;
- rollback action where authorized.

---

# 69. Build Dashboard

Build details should expose:

- commit SHA;
- status;
- duration;
- worker state;
- logs;
- artifact digest;
- failure reason.

---

# 70. Runtime Dashboard

Runtime details should expose:

- application status;
- container status;
- health;
- restart count;
- resource information;
- request metrics;
- logs.

---

# 71. Testing Strategy

Forge must use multiple testing layers.

## Unit Tests

Domain logic and state machines.

## Integration Tests

PostgreSQL, Kafka, Docker/BuildKit integration where practical.

## Contract Tests

HTTP and Kafka contract compatibility.

## End-to-End Tests

Full source-to-production workflow.

## Load Tests

Measure:

- throughput;
- latency;
- queue delay;
- deployment throughput;
- error rate.

## Failure Tests

Explicitly simulate failures.

---

# 72. Required Failure Tests

At minimum:

1. Duplicate webhook.
2. Duplicate Kafka event.
3. Kafka consumer crash.
4. Build worker crash.
5. Build timeout.
6. Failed Docker build.
7. Failed deployment health check.
8. Deployment cancellation.
9. Rollback.
10. Control Plane outage.
11. Runtime service outage.
12. PostgreSQL outage.
13. Cross-organization access.
14. Unauthorized deployment.
15. Malicious Dockerfile network access.
16. Secret exposure attempt.

---

# 73. Acceptance Criteria

Forge is not accepted merely because the relevant technology exists.

For example:

### Kafka

Not sufficient:

```text
Kafka container runs.
```

Required:

```text
Duplicate event
    ↓
Consumer receives twice
    ↓
One business effect
```

### Docker

Not sufficient:

```text
Docker container starts.
```

Required:

```text
Deploy new version
    ↓
Health check
    ↓
Traffic switch
```

### Observability

Not sufficient:

```text
Grafana dashboard exists.
```

Required:

```text
Request
 ↓
Trace
 ↓
Multiple services
 ↓
Kafka
 ↓
Runtime
```

can be correlated.

---

# 74. Capacity Assumptions

Initial design assumptions:

```text
10,000 organizations
50,000 users
50,000 projects
500,000 deployments/month
~16,000 deployments/day average
~1 deployment / 5 seconds average
~10 GB/day customer logs
10× peak multiplier assumption
```

These are design assumptions.

They are not claims about actual Forge capacity.

Load testing must establish measured limits.

---

# 75. Demo-Tier Capacity

The initial demonstration environment targets approximately:

```text
10 organizations
50 users
30 projects
100 deployments/day
1 GB logs/day
```

The implementation should remain usable on modest development infrastructure.

---

# 76. Retention

Initial retention targets:

| Data | Retention |
|---|---|
| Customer logs | 7 days |
| Build logs | 30 days |
| Deployment records | Long-lived |
| Audit logs | 1 year target |
| Live artifacts | Until no longer live |
| Rollback artifacts | According to rollback policy |

---

# 77. CI/CD

Forge itself must have CI/CD.

CI should include:

- compilation;
- unit tests;
- integration tests;
- static analysis;
- security checks;
- contract validation;
- Docker image builds.

Every service should be independently buildable.

---

# 78. Containerization

Every Forge service must have a Docker image.

Services should:

- run as non-root where practical;
- use minimal runtime images;
- expose health endpoints;
- handle graceful shutdown;
- emit structured logs;
- expose metrics.

---

# 79. Health Endpoints

Each service should provide:

```text
/health/live
/health/ready
```

Liveness answers:

> Is the process alive?

Readiness answers:

> Can this service currently accept work?

These must not be conflated.

---

# 80. Graceful Shutdown

Services must stop accepting new work before termination.

For Kafka consumers:

```text
Stop consumption
      ↓
Finish/release active work
      ↓
Commit appropriate offsets
      ↓
Shutdown
```

Long-running operations must remain recoverable.

---

# 81. Timeouts

Every external dependency must have bounded timeouts.

Examples:

- database;
- Kafka;
- GitHub;
- Docker;
- registry;
- health checks;
- internal HTTP calls.

No critical distributed operation should wait indefinitely.

---

# 82. Retry Policy

Retries use:

```text
Exponential backoff
+
Jitter
+
Maximum attempts
```

Not every failure should be retried.

Permanent errors should fail quickly.

---

# 83. Dead-Letter Handling

Events that repeatedly fail processing must become visible as problematic rather than being retried forever.

Dead-letter handling should include:

- event ID;
- failure reason;
- consumer;
- retry count;
- timestamp;
- original event metadata.

---

# 84. API Error Model

APIs should return structured errors.

Example:

```json
{
  "code": "DEPLOYMENT_ALREADY_RUNNING",
  "message": "An in-flight deployment already exists for this environment.",
  "requestId": "req_123"
}
```

Internal stack traces must not be exposed to users.

---

# 85. Logging

Logs must be structured.

Example fields:

```text
timestamp
level
service
requestId
traceId
organizationId
userId
projectId
event
message
```

Sensitive values must never appear in logs.

---

# 86. Data Protection

Sensitive data includes:

- passwords;
- access tokens;
- refresh tokens;
- secrets;
- GitHub credentials;
- internal service credentials.

These must not appear in:

- logs;
- error messages;
- audit metadata;
- Kafka events unless explicitly required and safely protected.

---

# 87. API Versioning

Public APIs should be versioned where compatibility requires it.

Example:

```text
/api/v1/projects
/api/v1/deployments
```

Breaking changes require a new API version.

---

# 88. Event Versioning

Kafka events use schema versions.

Example:

```text
DeploymentSucceeded.v1
DeploymentSucceeded.v2
```

Consumers should support compatible evolution.

---

# 89. Operational Runbooks

Forge should maintain runbooks for:

- Kafka outage;
- PostgreSQL outage;
- build worker failure;
- deployment stuck;
- runtime host failure;
- registry failure;
- certificate failure;
- high error rate;
- consumer lag;
- disk exhaustion.

This demonstrates operational maturity.

---

# 90. Distributed System Demonstrations

The project should include reproducible demonstrations.

### Demonstration 1 — Duplicate Event

```text
Publish same event twice
        ↓
Consumer
        ↓
One deployment effect
```

### Demonstration 2 — Worker Crash

```text
Start build
        ↓
Kill worker
        ↓
Recovery
        ↓
Build completes
```

### Demonstration 3 — Failed Deployment

```text
v1 healthy
        ↓
Deploy broken v2
        ↓
Health check fails
        ↓
v1 remains live
```

### Demonstration 4 — Control Plane Failure

```text
Stop control plane
        ↓
Existing application
        ↓
Still accessible
```

### Demonstration 5 — Tenant Attack

```text
Org B requests Org A resource
        ↓
Access denied
```

---

# 91. Product Success Criteria

Forge succeeds when it can demonstrate:

### Reliability

A worker can fail without permanently losing important work.

### Safety

A failed deployment does not replace a healthy application.

### Security

Users cannot cross organization boundaries.

### Consistency

Duplicate events do not create duplicate business effects.

### Observability

A distributed workflow can be traced across services.

### Recoverability

Failed operations can be retried or recovered.

### Immutability

Artifacts and deployment records remain historically accurate.

### Performance

Load testing provides measured system limits.

---

# 92. Engineering Evidence

The project should maintain evidence for major claims.

Examples:

```text
Load test results
Failure recovery logs
Trace screenshots
Deployment state transitions
Kafka consumer behavior
Security test results
Database constraints
API contract tests
```

The project must not claim:

> "Supports millions of users"

unless actual testing provides evidence.

---

# 93. MVP Scope

The MVP includes:

```text
Authentication
        ↓
Organization
        ↓
RBAC
        ↓
Project
        ↓
GitHub Integration
        ↓
Webhook
        ↓
Kafka
        ↓
Build Service
        ↓
Build Worker
        ↓
BuildKit
        ↓
OCI Registry
        ↓
Deployment Service
        ↓
Runtime Service
        ↓
Docker
        ↓
Reverse Proxy
        ↓
Health Check
        ↓
Live Application
```

Plus:

- logs;
- basic metrics;
- deployment history;
- rollback;
- audit;
- rate limiting;
- distributed tracing;
- CI/CD;
- failure testing.

---

# 94. Explicitly Deferred Features

The following are not MVP requirements:

- multi-region deployment;
- advanced autoscaling;
- canary deployments;
- blue/green deployment;
- service mesh;
- full customer distributed tracing;
- advanced customer metrics;
- managed databases;
- managed volumes;
- billing;
- enterprise SSO;
- GitLab;
- Bitbucket;
- advanced AI remediation;
- CDN;
- multi-cloud deployment.

---

# 95. Future Evolution

Future capabilities may include:

```text
Multiple Runtime Hosts
        ↓
Runtime Scheduler
        ↓
Autoscaling
        ↓
Kubernetes
        ↓
Multi-region
```

Additional event consumers may include:

```text
Kafka
 ├── Audit
 ├── Notifications
 ├── Analytics
 ├── Incident Detection
 └── AI Assistant
```

---

# 96. AI Future Architecture

AI is intentionally not part of the critical deployment path initially.

A future AI layer may analyze:

- deployment failures;
- logs;
- traces;
- metrics;
- incident timelines;
- recent configuration changes;
- deployment history.

Example:

```text
Incident
   │
   ├── Logs
   ├── Metrics
   ├── Traces
   ├── Deployment
   └── Configuration
          │
          ▼
      AI Analysis
          │
          ▼
     Explanation
          │
          ▼
    Suggested Action
```

AI should initially be advisory.

It should not autonomously mutate production state without explicit authorization and safety controls.

---

# 97. Architecture Principles

Forge follows these principles:

### 1. Explicit ownership

Every resource has a clear owner.

### 2. Immutable deployment identity

Artifacts and deployment metadata do not silently change.

### 3. Asynchronous long-running work

Builds and deployments do not block HTTP requests.

### 4. Failure is expected

Every important operation has a recovery path.

### 5. Security boundaries are explicit

Builds and customer applications are treated as untrusted.

### 6. Observability is part of the product

Logs, metrics and traces are first-class engineering requirements.

### 7. Evidence over claims

Performance and reliability claims require measurements.

### 8. Technology follows requirements

Kafka, microservices and future Kubernetes adoption must have architectural justification.

---

# 98. Critical Invariants

Forge must preserve these invariants:

1. At most one in-flight deployment per environment.
2. Deployment commit SHA never changes.
3. Deployment artifact digest never changes.
4. Deployment environment never changes.
5. Deployment configuration snapshot never changes.
6. Every organization has at least one Owner.
7. Audit records cannot be modified/deleted by application code.
8. Duplicate webhooks cannot create duplicate deployment effects.
9. Duplicate Kafka events cannot create duplicate business effects.
10. Users cannot access resources belonging to another organization.
11. Production secrets are never exposed to untrusted builds.
12. Deployments cannot become live without health checks.
13. Rollbacks create new deployments.
14. Live artifacts cannot be deleted.
15. Revoked credentials cannot authorize new operations.
16. Every important asynchronous operation has timeout/recovery behavior.
17. Control-plane failure does not stop running customer applications.
18. Every deployment references an immutable configuration snapshot.
19. Services cannot directly mutate another service's owned data.
20. PostgreSQL remains the authoritative business-state store.
21. Kafka events are durable, versioned, and idempotently consumed.

---

# 99. Open Architectural Decisions

The following can be finalized during implementation:

- exact API Gateway technology;
- JWT versus opaque access-token implementation details;
- Kafka deployment topology;
- Kafka partition strategy;
- exact event serialization format;
- schema registry selection;
- secrets encryption mechanism;
- OCI registry implementation;
- reverse proxy implementation;
- runtime resource limits;
- exact build-worker isolation level;
- exact PostgreSQL schema separation;
- log storage backend;
- metrics storage configuration.

These decisions should be recorded as ADRs rather than silently embedded into implementation.

---

# 100. Implementation Roadmap

## Phase 0 — Foundation

- repository;
- service templates;
- Docker Compose;
- PostgreSQL;
- Kafka;
- observability;
- CI;
- contracts;
- ADRs.

## Phase 1 — Identity

- users;
- authentication;
- sessions;
- organizations;
- RBAC.

## Phase 2 — Control Plane

- projects;
- environments;
- GitHub;
- webhooks;
- configuration;
- secrets.

## Phase 3 — Build

- Build Service;
- Kafka events;
- Build Worker;
- BuildKit;
- registry;
- immutable artifacts;
- build logs.

## Phase 4 — Deployment

- Deployment Service;
- state machine;
- health checks;
- safe rollout;
- rollback;
- cancellation.

## Phase 5 — Runtime

- Runtime Service;
- Docker;
- routing;
- reverse proxy;
- runtime health;
- application logs;
- metrics.

## Phase 6 — Reliability

- outbox;
- idempotency;
- retries;
- recovery;
- dead-letter handling;
- distributed tracing.

## Phase 7 — Operations

- dashboards;
- incidents;
- alerts;
- runbooks;
- load testing;
- failure testing;
- security testing.

---

# 101. Definition of Done

Forge MVP is complete only when the complete workflow works:

```text
GitHub Push
    ↓
Webhook
    ↓
Control Plane
    ↓
Kafka
    ↓
Build Service
    ↓
Build Worker
    ↓
BuildKit
    ↓
OCI Registry
    ↓
Deployment Service
    ↓
Runtime Service
    ↓
Docker Container
    ↓
Health Check
    ↓
Reverse Proxy
    ↓
Live Application
```

and the system can demonstrate:

- authentication;
- RBAC;
- tenant isolation;
- immutable artifacts;
- safe deployments;
- rollback;
- duplicate-event protection;
- worker recovery;
- control-plane independence;
- distributed tracing;
- structured logging;
- metrics;
- auditability;
- CI/CD;
- measurable performance.

---

# 102. Product-Level Principle

Forge should not optimize for the number of technologies used.

It should optimize for the number of **real engineering problems solved correctly**.

The difference is:

```text
Technology Demo

React
Spring Boot
Kafka
Docker
PostgreSQL
```

versus:

```text
Production Engineering

How does Forge behave when
a Kafka event is duplicated?

How does Forge recover when
a build worker dies?

How does Forge prevent a broken
deployment from replacing a healthy one?

How does Forge prevent Org A
from accessing Org B?

How does Forge continue serving
customer traffic when the control
plane is unavailable?

How can an engineer trace one
deployment across five services?

How do we prove the system
can handle its claimed workload?
```

Forge is successful when it can answer the second set with working software and evidence.

---

# 103. Final Product Definition

Forge is a **small but deeply engineered distributed developer platform**.

Its core architecture is:

```text
React + TypeScript
        │
        ▼
   API Gateway
        │
        ├── Identity Service
        ├── Control Plane Service
        └── Deployment Service
                │
                ├── Kafka
                ├── Build Service
                │      └── Build Workers
                │             └── BuildKit
                │
                └── Runtime Service
                       └── Docker
                              └── Reverse Proxy
```

with:

```text
PostgreSQL
    = authoritative business state

Kafka
    = asynchronous event backbone

OCI Registry
    = immutable artifacts

Docker
    = initial runtime

OpenTelemetry
    = distributed observability
```

The system is intentionally designed to evolve from a small distributed platform into a larger production architecture without introducing complexity that cannot be justified.

**Forge does not attempt to look production-grade.  
Forge attempts to behave production-grade.**