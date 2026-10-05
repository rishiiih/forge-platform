# Forge — System Architecture

**Version:** 1.0  
**Status:** Target Architecture  
**Architecture Style:** Purposeful Microservices + Event-Driven Architecture  
**Primary Backend:** Java 21 + Spring Boot  
**Frontend:** React + TypeScript + Vite  
**Database:** PostgreSQL  
**Event Backbone:** Apache Kafka  
**Runtime:** Docker  
**Build Infrastructure:** Docker + BuildKit + OCI Registry  
**Observability:** OpenTelemetry + Prometheus + Grafana

---

# 1. Architecture Overview

Forge is a production-oriented container deployment platform.

The system accepts source code from a connected GitHub repository, builds an immutable OCI image, deploys that image to a Docker runtime, performs health checks, exposes the application through a reverse proxy, and provides deployment history, logs, metrics, rollback, auditing, and operational visibility.

Forge is intentionally designed as a **small but genuine distributed system**.

The architecture is not divided into dozens of microservices simply to appear sophisticated.

Instead, each service owns a meaningful business or infrastructure boundary.

The primary services are:

1. Identity Service
2. Control Plane Service
3. Build Service
4. Deployment Service
5. Runtime Service

Supporting infrastructure includes:

- API Gateway
- Apache Kafka
- PostgreSQL
- Build Workers
- BuildKit
- OCI Registry
- Docker Runtime
- Reverse Proxy
- OpenTelemetry Collector
- Prometheus
- Grafana

---

# 2. Design Philosophy

Forge follows several architectural principles.

## 2.1 Service boundaries must represent real responsibilities

A service should exist because it owns a domain or infrastructure responsibility.

Bad:

```text
UserService
ProjectService
EnvironmentService
RepositoryService
DeploymentService
LogService
MetricService
NotificationService
...
```

Good:

```text
Identity
Control Plane
Build
Deployment
Runtime
```

The goal is meaningful isolation rather than maximum service count.

---

# 3. High-Level Architecture

```text
                           ┌─────────────────────┐
                           │      Browser        │
                           │   React + TypeScript│
                           └──────────┬──────────┘
                                      │
                                      ▼
                           ┌─────────────────────┐
                           │    API Gateway      │
                           │ TLS / Routing /     │
                           │ Rate Limits /       │
                           │ Request IDs         │
                           └──────────┬──────────┘
                                      │
             ┌────────────────────────┼────────────────────────┐
             │                        │                        │
             ▼                        ▼                        ▼
     ┌───────────────┐       ┌────────────────┐       ┌────────────────┐
     │   Identity    │       │ Control Plane  │       │   Deployment   │
     │   Service     │       │    Service     │       │    Service     │
     └───────┬───────┘       └───────┬────────┘       └───────┬────────┘
             │                       │                        │
             │                       │                        ▼
             │                       │                 ┌──────────────┐
             │                       │                 │    Runtime   │
             │                       │                 │    Service   │
             │                       │                 └──────┬───────┘
             │                       │                        │
             │                       │                        ▼
             │                       │                  Docker Runtime
             │                       │                        │
             │                       │                        ▼
             │                       │                  Reverse Proxy
             │                       │
             │                       ▼
             │                ┌───────────────┐
             │                │    Kafka      │
             └────────────────► Event Backbone│
                              └───────┬───────┘
                                      │
                       ┌──────────────┼──────────────┐
                       │              │              │
                       ▼              ▼              ▼
                ┌────────────┐ ┌────────────┐ ┌────────────┐
                │   Build    │ │ Deployment │ │   Audit /  │
                │   Service  │ │ Consumers  │ │ Other      │
                └─────┬──────┘ └────────────┘ │ Consumers  │
                      │                       └────────────┘
                      ▼
                ┌────────────┐
                │Build Worker│
                └─────┬──────┘
                      │
                      ▼
                   BuildKit
                      │
                      ▼
                OCI Registry


                 ┌───────────────────────────────┐
                 │          PostgreSQL           │
                 │                               │
                 │  Service-owned schemas        │
                 │  Business state               │
                 │  Outbox / Idempotency         │
                 └───────────────────────────────┘

                 ┌───────────────────────────────┐
                 │      Observability Stack      │
                 │                               │
                 │ OpenTelemetry → Prometheus    │
                 │              → Grafana         │
                 └───────────────────────────────┘
```

---

# 4. Architectural Boundaries

Forge contains two fundamentally different categories of systems.

## 4.1 Control Plane

The control plane manages:

- users
- organizations
- projects
- environments
- repositories
- configuration
- builds
- deployments
- permissions
- audit records
- orchestration

The control plane does not directly serve customer application traffic.

---

# 5. Data Plane

The runtime/data plane manages customer workloads.

```text
Deployment Service
       │
       ▼
Runtime Service
       │
       ▼
Docker
       │
       ▼
Customer Container
       │
       ▼
Reverse Proxy
       │
       ▼
Internet
```

The most important property is:

> A control-plane outage must not automatically terminate already-running customer applications.

Once a deployment is live, the runtime should continue operating independently of the availability of the control plane.

---

# 6. Trust Boundaries

Forge processes untrusted customer code.

The major trust boundaries are:

```text
Browser
   │
   ▼
API Gateway
   │
   ├──────── Trusted Forge Services
   │
   └──────── Build Boundary
                 │
                 ▼
          Untrusted Repository
                 │
                 ▼
             Dockerfile
                 │
                 ▼
           Build Container
```

Customer Dockerfiles must be treated as hostile input.

A Dockerfile may attempt to:

- access the host filesystem
- access internal services
- steal credentials
- consume excessive resources
- modify build infrastructure
- attack other workloads

Therefore:

> Build execution is a security boundary, not simply another application feature.

---

# 7. Service Architecture

## 7.1 Identity Service

### Responsibilities

The Identity Service owns:

- users
- authentication
- sessions
- credentials
- personal organizations
- organization membership
- organization roles
- authorization primitives

### Roles

```text
OWNER
ADMIN
DEVELOPER
VIEWER
```

### Invariants

- Every organization has at least one Owner.
- Authentication credentials are never exposed through normal APIs.
- Revoked credentials cannot authorize new requests.
- Authorization must be enforced server-side.

---

# 8. Control Plane Service

The Control Plane owns the core project configuration.

### Responsibilities

- organizations
- projects
- environments
- GitHub repositories
- branches
- Dockerfile configuration
- build context
- ports
- environment configuration
- runtime configuration
- health-check configuration
- deployment settings
- auto-deployment settings

It also handles GitHub integration and webhook processing.

---

# 9. Build Service

The Build Service owns the lifecycle of builds.

```text
Commit SHA
    │
    ▼
Build Requested
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
Immutable Digest
```

### Build states

```text
QUEUED
RUNNING
SUCCESS
FAILED
TIMEOUT
CANCELLED
```

A successful build produces an immutable artifact.

Example:

```text
sha256:8f2c...
```

The digest is authoritative.

Tags are not authoritative deployment identities.

---

# 10. Build Worker

Build Workers execute untrusted builds.

The worker is separated from the main control plane.

```text
Build Service
     │
     ▼
Build Worker
     │
     ▼
BuildKit
     │
     ▼
OCI Registry
```

A build worker should not have:

- production credentials
- unrestricted PostgreSQL access
- unrestricted internal network access
- Forge signing credentials
- production runtime secrets

Build-time secrets and runtime secrets are separate concepts.

---

# 11. Deployment Service

The Deployment Service owns deployment state transitions.

### Deployment states

```text
QUEUED
   │
   ▼
DEPLOYING
   │
   ▼
HEALTH_CHECKING
   │
   ├──────────────► FAILED
   │
   ▼
SUCCESS
```

Other terminal states may include:

```text
TIMEOUT
CANCELLED
SUPERSEDED
```

---

# 12. Deployment Invariants

For every environment:

```text
At most one deployment may be actively modifying production state.
```

A deployment's:

- commit SHA
- artifact digest
- environment
- configuration snapshot

must never change after creation.

---

# 13. Safe Deployment Algorithm

Forge does not immediately replace the running application.

Instead:

```text
Current Version
      │
      │
      ▼
Start New Container
      │
      ▼
Wait for Startup
      │
      ▼
Health Checks
      │
      ├──────────────► FAIL
      │                  │
      │                  ▼
      │             Remove New Container
      │
      ▼
Healthy
      │
      ▼
Switch Reverse Proxy
      │
      ▼
New Version Live
```

This guarantees that a failed deployment does not automatically take down the currently healthy application.

---

# 14. Health Checks

Health checks are configurable per environment.

Configuration may include:

- path
- port
- timeout
- interval
- retry count
- startup grace period
- success threshold

A deployment cannot become live until its health-check policy succeeds.

---

# 15. Rollbacks

Rollback is not mutation of an existing deployment.

Example:

```text
Deployment 101
artifact = sha256:A
       │
       ▼
Deployment 102
artifact = sha256:B
       │
       ▼
Failure
       │
       ▼
Rollback
       │
       ▼
Deployment 103
artifact = sha256:A
```

Deployment 103 is a new deployment record.

This preserves deployment history.

---

# 16. Cancellation

Deployment cancellation is supported until the irreversible proxy-switch stage.

Conceptually:

```text
QUEUED          → cancellable
DEPLOYING       → cancellable
HEALTH_CHECKING → cancellable
PROXY SWITCH    → irreversible
```

Once traffic is switched, cancellation becomes rollback rather than cancellation.

---

# 17. Runtime Service

The Runtime Service owns customer workloads.

Responsibilities include:

- container lifecycle
- runtime state
- route configuration
- health state
- container logs
- runtime metadata
- stopping workloads
- starting workloads
- removing workloads

The runtime should remain operational even if the control plane becomes temporarily unavailable.

---

# 18. Reverse Proxy

The reverse proxy maps public routes to healthy customer containers.

Conceptually:

```text
https://app.forge.example
          │
          ▼
     Reverse Proxy
          │
          ▼
     Container :8080
```

Traffic should only be switched to a new deployment after successful health checks.

---

# 19. PostgreSQL Architecture

PostgreSQL is the authoritative store for business state.

Initially, Forge uses one PostgreSQL cluster.

However, services own their data logically.

Example:

```text
forge_identity
forge_control
forge_build
forge_deployment
forge_runtime
```

These may initially exist as schemas within one PostgreSQL instance.

---

# 20. Database Ownership

Each service owns its domain data.

```text
Identity
 └── users
 └── sessions
 └── credentials
 └── memberships

Control Plane
 └── organizations
 └── projects
 └── environments
 └── repositories

Build
 └── builds
 └── artifacts
 └── build metadata

Deployment
 └── deployments
 └── deployment attempts
 └── state transitions

Runtime
 └── runtime instances
 └── routes
 └── health state
```

Services must not directly mutate another service's tables.

---

# 21. PostgreSQL Does Not Mean Distributed Transactions

Forge does not use distributed ACID transactions across services.

Instead:

```text
Local transaction
      +
Outbox event
      +
Kafka
      +
Idempotent consumer
      +
Compensating action
```

This is the fundamental consistency model.

---

# 22. Kafka Event Backbone

Kafka provides asynchronous communication between services.

It is not the authoritative source of business state.

```text
PostgreSQL
    │
    │ authoritative state
    ▼
Service
    │
    ▼
Outbox
    │
    ▼
Kafka
    │
    ├────────► Build Service
    ├────────► Deployment Service
    ├────────► Audit Consumer
    └────────► Other Consumers
```

PostgreSQL answers:

> What is the current state?

Kafka answers:

> What happened?

---

# 23. Initial Event Types

Forge initially defines events such as:

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

Events should represent meaningful domain changes rather than arbitrary internal method calls.

---

# 24. Event Envelope

Events contain common metadata.

Example conceptual structure:

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

# 25. Event Delivery Semantics

Kafka consumers must assume:

> An event may be delivered more than once.

Therefore consumers must be idempotent.

Example:

```text
Kafka
  │
  ▼
Deployment Service
  │
  ├── event already processed?
  │       │
  │       ├── YES → ignore
  │       └── NO
  │
  ▼
Perform operation
  │
  ▼
Record event ID
```

Duplicate delivery must not cause duplicate deployment side effects.

---

# 26. Transactional Outbox

A service must not perform:

```text
UPDATE database
      ↓
publish Kafka event
```

with two unrelated operations.

If the process crashes between them, the system may become inconsistent.

Instead:

```text
BEGIN TRANSACTION

Update business state

Insert event into outbox

COMMIT
```

A separate publisher then reads the outbox and publishes events to Kafka.

This provides reliable state-to-event publication.

---

# 27. Outbox Flow

```text
                    PostgreSQL
                 ┌──────────────┐
                 │ Business Data│
                 ├──────────────┤
                 │ Outbox       │
                 └──────┬───────┘
                        │
                        ▼
                 Outbox Publisher
                        │
                        ▼
                      Kafka
```

The outbox publisher may retry safely.

---

# 28. Idempotency

Idempotency is required at multiple levels.

### Webhooks

Duplicate GitHub deliveries must not trigger duplicate builds.

### Kafka

Duplicate events must not create duplicate business effects.

### API Requests

Retryable client requests should not create duplicate resources where idempotency is required.

### Deployment

A deployment operation must have a stable identity.

---

# 29. GitHub Webhooks

Forge validates GitHub webhook signatures before processing.

Flow:

```text
GitHub
  │
  ▼
API Gateway / Webhook Endpoint
  │
  ▼
Signature Validation
  │
  ├── Invalid → Reject
  │
  ▼
Deduplication
  │
  ▼
Persist Webhook Event
  │
  ▼
CommitPushed Event
  │
  ▼
Kafka
```

The GitHub delivery ID is used to prevent duplicate processing.

---

# 30. Automatic Deployment

Typical flow:

```text
GitHub Push
    │
    ▼
Webhook
    │
    ▼
CommitPushed
    │
    ▼
Kafka
    │
    ▼
Build Service
    │
    ▼
Build Worker
    │
    ▼
Immutable Image
    │
    ▼
BuildCompleted
    │
    ▼
Kafka
    │
    ▼
Deployment Service
    │
    ▼
Runtime Service
    │
    ▼
Health Check
    │
    ▼
Proxy Switch
```

---

# 31. Deployment Configuration Snapshots

Every deployment references an immutable configuration snapshot.

The snapshot includes relevant deployment configuration such as:

- environment variables
- secrets references
- image
- port
- health-check configuration
- runtime settings

Changing project configuration later must not modify historical deployment records.

---

# 32. Secrets

Secrets are never returned through normal APIs.

Operations include:

```text
CREATE
ROTATE
DELETE
```

The system stores references or encrypted representations rather than exposing secret values in ordinary responses.

Runtime secrets must never automatically become available to untrusted builds.

---

# 33. Multi-Tenancy

Every organization-owned resource is associated with an organization.

Conceptually:

```text
Organization
    │
    ├── Members
    ├── Projects
    │      ├── Environments
    │      ├── Builds
    │      └── Deployments
    │
    └── Secrets
```

Every request must enforce:

```text
Authentication
      +
Organization membership
      +
Role permission
      +
Resource ownership
```

---

# 34. IDOR Prevention

The following is insufficient:

```text
GET /projects/{projectId}
```

followed by:

```text
SELECT * FROM projects WHERE id = ?
```

The query must also enforce ownership or authorized organization membership.

Conceptually:

```sql
SELECT *
FROM projects
WHERE id = ?
  AND organization_id = ?
```

Cross-organization access must be impossible through normal APIs.

---

# 35. API Gateway

The API Gateway provides infrastructure-level edge concerns.

Responsibilities:

- TLS termination
- request routing
- CORS
- request IDs
- request-size limits
- external API versioning
- rate limiting
- authentication token forwarding
- basic request validation

Business authorization remains the responsibility of the owning service.

---

# 36. API Versioning

External APIs should be versioned.

Example:

```text
/api/v1/projects
/api/v1/deployments
/api/v1/builds
```

Breaking API changes require a new version or controlled migration strategy.

---

# 37. Rate Limiting

Forge uses endpoint-specific rate limits.

Examples:

```text
Authentication
Webhook ingestion
Deployment creation
Build creation
General API requests
```

Responses exceeding limits should return:

```text
HTTP 429
Retry-After
```

Security-sensitive operations may fail closed if the rate-limiter subsystem is unavailable.

---

# 38. Asynchronous Processing

Long-running operations should not block HTTP requests.

Examples:

```text
Docker builds
Deployments
Health-check workflows
Log processing
Artifact cleanup
Webhook processing
```

The API should typically return an operation identifier.

Example:

```text
POST /api/v1/deployments

202 Accepted

{
    "deploymentId": "...",
    "status": "QUEUED"
}
```

---

# 39. Build Worker Reliability

Workers can crash.

Therefore:

```text
Job
 │
 ▼
Lease
 │
 ▼
Worker
 │
 ├── success
 │
 └── crash
       │
       ▼
Lease expires
       │
       ▼
Recovery
```

Long-running operations require:

- leases
- heartbeats
- timeout
- retry policy
- recovery logic

---

# 40. Retry Strategy

Retries use:

```text
Exponential Backoff
+
Jitter
+
Maximum Attempts
```

Example:

```text
Attempt 1 → immediate
Attempt 2 → short delay
Attempt 3 → longer delay
Attempt 4 → longer delay
...
```

Retryable and non-retryable failures must be distinguished.

---

# 41. Dead-Letter Handling

After maximum retry attempts, a failed operation must not disappear.

It enters a terminal failure state.

Example:

```text
RUNNING
   │
   ▼
FAILED
   │
 retry
   ▼
FAILED
   │
 retry
   ▼
DEAD
```

Dead jobs/events must be observable and diagnosable.

---

# 42. Customer Logs

MVP customer observability includes:

- container stdout
- container stderr
- basic runtime/request metrics
- deployment logs

Customer log retention target:

```text
7 days
```

Build log retention target:

```text
30 days
```

Deployment records are retained much longer.

---

# 43. Customer Metrics

MVP request metrics may include:

- request count
- response status
- latency
- error rate

Forge does not attempt to become a full Datadog replacement.

Advanced customer telemetry is intentionally deferred.

---

# 44. Forge Observability

Forge itself must be observable.

The architecture uses:

```text
Application Services
       │
       ▼
OpenTelemetry
       │
       ▼
OTel Collector
       │
       ├──────► Prometheus
       │
       └──────► Logging / Trace Backend
                     │
                     ▼
                  Grafana
```

---

# 45. Distributed Tracing

A request should maintain correlation across services.

Example:

```text
Browser
  │ traceId=ABC
  ▼
API Gateway
  │
  ▼
Deployment Service
  │
  ▼
Kafka
  │
  ▼
Runtime Service
  │
  ▼
Docker
```

The trace ID should remain associated with the operation where technically possible.

This allows investigation of distributed failures.

---

# 46. Request Correlation

Every incoming request receives a request ID.

Example:

```text
X-Request-ID: 8e7c...
```

The identifier is propagated to:

- service logs
- events
- deployment records where appropriate
- asynchronous operations
- traces

---

# 47. Metrics

Forge exposes operational metrics for:

### API

- request count
- latency
- error rate
- status codes

### Kafka

- consumer lag
- publish failures
- processing latency
- retry counts

### Builds

- queue duration
- build duration
- failure rate
- timeout rate

### Deployments

- queue duration
- deployment duration
- health-check failures
- rollback count

### Runtime

- running containers
- unhealthy containers
- container restart count

---

# 48. Incidents

Forge supports operational incidents.

Example alert:

```text
5xx rate > 5%
for 5 consecutive minutes
```

Incident states:

```text
OPEN
ACKNOWLEDGED
MITIGATED
RESOLVED
```

An incident should reference:

- organization
- project
- environment
- condition
- detection time
- related deployment where applicable

---

# 49. Audit Logging

Security-sensitive actions generate audit records.

Examples:

```text
User invited
Role changed
Project created
Secret rotated
Deployment triggered
Deployment cancelled
Rollback executed
Project deletion requested
```

Audit records are append-only from the application perspective.

Target retention:

```text
1 year
```

---

# 50. Project Deletion

Deletion is not an immediate database delete.

Conceptual lifecycle:

```text
ACTIVE
   │
   ▼
DELETION_REQUESTED
   │
   ▼
STOPPING_WORKLOADS
   │
   ▼
STOPPED
   │
   ▼
DELETED
```

This avoids leaving active customer workloads after their project has supposedly been removed.

---

# 51. Failure Model

Forge explicitly assumes failures.

Possible failures include:

- API Gateway crash
- Identity Service crash
- Control Plane crash
- Build Service crash
- Deployment Service crash
- Runtime Service crash
- Kafka outage
- PostgreSQL outage
- worker crash
- Docker failure
- registry failure
- health-check failure
- network failure
- duplicate event delivery

The system should define what happens for each.

---

# 52. Identity Service Failure

Existing authenticated sessions may remain usable depending on token/session architecture.

New authentication operations may fail.

Existing customer applications should continue running.

---

# 53. Control Plane Failure

During a Control Plane outage:

```text
Existing deployments
        │
        ▼
Existing containers
        │
        ▼
Continue serving traffic
```

New project/configuration operations may temporarily fail.

---

# 54. Build Service Failure

A Build Service failure must not terminate existing customer applications.

Queued builds may remain in Kafka/database-backed state and recover when the service returns.

---

# 55. Deployment Service Failure

An in-progress deployment must have recoverable state.

A service restart must not leave an environment permanently stuck in:

```text
DEPLOYING
```

Deployment reconciliation must detect stale operations and recover them.

---

# 56. Runtime Service Failure

The Runtime Service is directly associated with the customer data plane.

Its failure may affect:

- new deployments
- runtime management
- health information

However, already-running containers should not automatically be terminated simply because the control component is unavailable.

---

# 57. Kafka Failure

Kafka is an asynchronous communication layer.

If Kafka becomes unavailable:

```text
Business state
      │
      ▼
PostgreSQL
```

remains authoritative.

Services should:

- retry publishing
- retain outbox records
- avoid losing committed state
- recover publication after Kafka returns

---

# 58. PostgreSQL Failure

PostgreSQL is critical control-plane infrastructure.

If PostgreSQL becomes unavailable:

- new state mutations may fail
- authentication may be degraded
- deployments may be blocked
- configuration changes may fail

However, existing runtime workloads should continue serving traffic.

---

# 59. Runtime/Data-Plane Independence

This is a central architectural invariant:

> Control-plane failure must not automatically become customer application failure.

Conceptually:

```text
                 Control Plane
                     │
                     │
                     X   ← temporary failure
                     │
                     │
Customer Traffic ────┼────────► Runtime
                                  │
                                  ▼
                              Containers
```

---

# 60. Artifact Immutability

A successful build produces an immutable artifact.

Example:

```text
Build 42
    │
    ▼
sha256:ABC123
```

Deployments reference:

```text
artifact_digest = sha256:ABC123
```

The digest never changes.

---

# 61. Artifact Retention

Artifacts required for:

- current production
- configured rollback window
- active deployments

must not be deleted.

Cleanup processes must understand deployment references before deleting artifacts.

---

# 62. Configuration Immutability

Deployment records reference immutable configuration snapshots.

Historical deployments therefore remain reproducible and auditable.

---

# 63. Frontend Architecture

The frontend uses:

```text
React
TypeScript
Vite
React Router
TanStack Query
Tailwind CSS
```

Zustand may be introduced when global client state genuinely requires it.

The frontend should not become the source of truth for authorization or deployment state.

---

# 64. Frontend Areas

Primary UI areas:

```text
Dashboard
Organizations
Projects
Environments
Repository Configuration
Builds
Deployments
Deployment Details
Runtime
Logs
Metrics
Incidents
Audit Logs
Settings
```

---

# 65. Deployment UI

A deployment page should show:

```text
Deployment
├── Status
├── Commit SHA
├── Artifact Digest
├── Environment
├── Configuration Snapshot
├── Timeline
├── Build
├── Health Checks
├── Runtime
└── Rollback
```

The UI should make state transitions visible.

---

# 66. Repository Structure

A possible repository structure is:

```text
forge/
│
├── frontend/
│
├── services/
│   ├── identity-service/
│   ├── control-plane-service/
│   ├── build-service/
│   ├── deployment-service/
│   └── runtime-service/
│
├── workers/
│   └── build-worker/
│
├── gateway/
│
├── contracts/
│   ├── api/
│   └── events/
│
├── infrastructure/
│   ├── docker/
│   ├── kafka/
│   ├── postgres/
│   ├── prometheus/
│   ├── grafana/
│   └── otel/
│
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   └── adr/
│
└── docker-compose.yml
```

---

# 67. Backend Service Structure

Each Spring Boot service should follow a consistent internal structure.

Example:

```text
src/main/java/com/forge/deployment/

├── api/
├── application/
├── domain/
├── infrastructure/
└── config/
```

### `api`

HTTP controllers and request/response models.

### `application`

Use cases and orchestration.

### `domain`

Business rules and domain models.

### `infrastructure`

Database, Kafka, external APIs, Docker clients, etc.

---

# 68. Domain-Driven Boundaries

Each service should keep domain logic close to its owner.

For example:

```text
Deployment Service
    ├── Deployment
    ├── DeploymentAttempt
    ├── DeploymentState
    ├── HealthCheck
    └── RollbackPolicy
```

The Runtime Service should not contain deployment business rules.

Likewise, the Build Service should not decide whether a production deployment is authorized.

---

# 69. Inter-Service Communication

Forge uses two communication styles.

## Synchronous

REST/HTTP for operations that require an immediate response.

Example:

```text
Frontend
   │
   ▼
API Gateway
   │
   ▼
Control Plane
```

## Asynchronous

Kafka for events and long-running workflows.

Example:

```text
BuildCompleted
      │
      ▼
Kafka
      │
      ▼
Deployment Service
```

---

# 70. Synchronous Communication Rule

Synchronous service-to-service calls should be used carefully.

Avoid chains such as:

```text
A → B → C → D → E
```

for every request.

Long synchronous dependency chains increase:

- latency
- failure propagation
- operational complexity

Events should be used when immediate responses are not required.

---

# 71. Timeout Policy

Every network call must have an explicit timeout.

No service should wait indefinitely for another service.

Conceptually:

```text
Request
   │
   ▼
Timeout
   │
   ├── success
   └── failure
```

---

# 72. Graceful Shutdown

Services must support graceful shutdown.

On shutdown:

```text
Stop accepting new work
        │
        ▼
Finish safe in-flight work
        │
        ▼
Commit offsets / state
        │
        ▼
Close connections
        │
        ▼
Exit
```

This is especially important for Kafka consumers and deployment operations.

---

# 73. Health Endpoints

Each service exposes:

```text
/health/live
/health/ready
```

### Liveness

Answers:

> Is the process alive?

### Readiness

Answers:

> Can this service currently receive traffic?

Dependencies should be handled carefully so a temporary downstream outage does not necessarily create cascading restarts.

---

# 74. Testing Strategy

Forge requires multiple testing layers.

```text
Unit Tests
    │
    ▼
Integration Tests
    │
    ▼
Contract Tests
    │
    ▼
End-to-End Tests
    │
    ▼
Load Tests
    │
    ▼
Failure Tests
```

---

# 75. Unit Tests

Test domain rules such as:

- deployment state transitions
- role permissions
- rollback rules
- health-check decisions
- artifact immutability
- project deletion states

---

# 76. Integration Tests

Test:

- PostgreSQL
- Kafka
- GitHub webhook processing
- Build Service
- Deployment Service
- Runtime Service
- Docker integration

Testcontainers can be used for realistic infrastructure dependencies.

---

# 77. Contract Tests

API and event contracts must be validated.

Examples:

```text
BuildCompleted event
DeploymentRequested event
RuntimeHealthChanged event
```

Breaking event-schema changes should be detected before deployment.

---

# 78. End-to-End Test

A core E2E test should execute:

```text
Create account
   ↓
Create organization
   ↓
Create project
   ↓
Connect repository
   ↓
Push commit
   ↓
Webhook
   ↓
Build
   ↓
Artifact
   ↓
Deployment
   ↓
Health Check
   ↓
Proxy Switch
   ↓
Application Live
```

---

# 79. Required Failure Tests

Forge must intentionally test failure.

Required scenarios include:

1. Duplicate GitHub webhook
2. Duplicate Kafka event
3. Kafka consumer crash
4. Build worker crash
5. Build timeout
6. Failed Docker build
7. Failed health check
8. Deployment cancellation
9. Rollback
10. Control-plane outage
11. Runtime-service outage
12. PostgreSQL outage
13. Unauthorized deployment
14. Cross-organization access
15. Malicious Dockerfile
16. Secret exposure attempt

---

# 80. Load Testing

Capacity assumptions are not production claims.

Initial design assumptions:

```text
10,000 organizations
50,000 users
50,000 projects
500,000 deployments/month
~16,000 deployments/day
~1 deployment every 5 seconds on average
~10 GB customer logs/day
~10× peak multiplier
```

These values must eventually be validated with load tests.

---

# 81. Demo Capacity

The initial demonstration environment targets approximately:

```text
10 organizations
50 users
30 projects
100 deployments/day
1 GB logs/day
```

This is a development/demo target, not a production guarantee.

---

# 82. Local Development

Docker Compose should provide the initial local infrastructure.

Conceptually:

```text
docker compose up
```

starts:

```text
PostgreSQL
Kafka
Kafka UI
OCI Registry
Prometheus
Grafana
OTel Collector
Reverse Proxy
```

Forge services can run locally through their Spring Boot development environment or containers.

---

# 83. Production Deployment Model

The first production-like Forge deployment environment remains intentionally small.

```text
                  Internet
                     │
                     ▼
                Reverse Proxy
                     │
          ┌──────────┴──────────┐
          │                     │
          ▼                     ▼
      API Gateway          Customer Apps
          │
          ▼
    Forge Services
          │
     ┌────┴────┐
     ▼         ▼
 PostgreSQL   Kafka
```

Runtime capacity may initially be one Docker host.

The architecture is designed so additional runtime hosts can be introduced later.

---

# 84. Evolution Path

Forge evolves through measured bottlenecks.

## Stage 1

```text
Microservices
+
Single PostgreSQL
+
Single Kafka cluster
+
Single runtime host
```

## Stage 2

```text
Multiple service instances
+
Load balancing
+
Multiple workers
```

## Stage 3

```text
Multiple runtime hosts
+
Runtime scheduler
```

## Stage 4

```text
Host capacity management
+
Autoscaling
```

## Stage 5

```text
Kubernetes-based runtime
```

Kubernetes is therefore an evolution rather than an arbitrary starting dependency.

---

# 85. Why Not Kubernetes Initially?

Kubernetes would introduce a substantial operational surface:

- control plane
- scheduler
- controllers
- networking
- service discovery
- ingress
- storage
- RBAC
- cluster lifecycle

Forge's primary learning objective is understanding the deployment platform itself.

Therefore the initial runtime remains Docker-based.

---

# 86. Why Kafka?

Kafka is justified by Forge's asynchronous workflows.

Examples:

```text
CommitPushed
BuildCompleted
DeploymentSucceeded
RuntimeHealthChanged
```

These events may have multiple consumers.

Kafka allows:

```text
Build Service
Deployment Service
Audit
Notifications
Analytics
```

to independently consume domain events.

Kafka is therefore an architectural dependency with a clear purpose rather than a technology checklist item.

---

# 87. Why Microservices?

Forge intentionally demonstrates:

- independent service ownership
- distributed communication
- asynchronous processing
- event delivery
- idempotency
- service failure isolation
- distributed tracing
- independent deployment
- data ownership
- eventual consistency

The architecture is therefore suitable for demonstrating production software engineering concepts in interviews.

---

# 88. What Forge Does Not Claim

Forge does not claim to provide:

- global multi-region infrastructure
- millions of users
- Kubernetes-level orchestration
- enterprise-grade isolation
- fully managed databases
- global CDN
- advanced autoscaling
- full Datadog functionality

Unless measured and validated, these remain future goals rather than claims.

---

# 89. Deferred Architecture

The following are intentionally deferred:

```text
Multi-region deployment
Advanced autoscaling
Canary deployments
Blue/green deployments
Service mesh
Customer distributed tracing
Advanced customer metrics
Managed databases
Managed persistent volumes
Billing
Enterprise SSO
GitLab
Bitbucket
CDN
Multi-cloud
Advanced AI remediation
```

---

# 90. AI Architecture — Future

AI should not be embedded directly into core deployment control paths initially.

A future architecture may look like:

```text
Forge Events
     │
     ▼
Observability Data
     │
     ▼
AI Analysis Service
     │
     ├── Failure explanation
     ├── Log summarization
     ├── Deployment diagnosis
     └── Suggested remediation
```

AI recommendations should initially be advisory.

The AI system should not automatically execute production remediation without explicit safety controls.

---

# 91. Security Principles

Forge follows:

```text
Least Privilege
Defense in Depth
Zero Trust Between Services
Explicit Authorization
Immutable Artifacts
Secret Isolation
Auditability
Fail-Safe Deployment
```

---

# 92. Core Security Invariants

1. Users cannot access another organization's resources.
2. Production secrets cannot be exposed to untrusted builds.
3. Revoked credentials cannot authorize new requests.
4. Webhook signatures are verified.
5. Duplicate events cannot cause duplicate side effects.
6. Build execution is isolated from the control plane.
7. Historical deployments cannot be silently modified.
8. Audit records cannot be modified through normal application APIs.

---

# 93. Core Reliability Invariants

1. At most one active deployment per environment.
2. A deployment's commit SHA never changes.
3. A deployment's artifact digest never changes.
4. A deployment's environment never changes.
5. A deployment cannot become live before health checks succeed.
6. Rollback creates a new deployment.
7. Running customer applications survive control-plane outages.
8. Non-terminal asynchronous work has recovery semantics.
9. Live artifacts cannot be accidentally deleted.
10. Duplicate events cannot create duplicate business effects.

---

# 94. Operational Invariants

Every production operation should answer:

```text
Who initiated it?
What resource changed?
Why did it change?
When did it change?
What version was involved?
What happened afterward?
```

This information should be recoverable through deployment history, audit logs, events, and observability data.

---

# 95. Architecture Decision Records

Forge should maintain ADRs for important decisions.

Initial ADR list:

```text
ADR-001 Microservices over Modular Monolith
ADR-002 Kafka as Event Backbone
ADR-003 PostgreSQL as Authoritative State
ADR-004 Transactional Outbox
ADR-005 Service-Owned Data
ADR-006 Immutable OCI Artifacts
ADR-007 Safe Deployment Strategy
ADR-008 Docker Runtime before Kubernetes
ADR-009 Build Isolation Model
ADR-010 Distributed Tracing
ADR-011 API Versioning
ADR-012 Event Schema Versioning
```

Each ADR should explain:

```text
Context
Decision
Alternatives
Trade-offs
Consequences
```

---

# 96. Observability-First Development

A service is not considered operationally complete simply because its endpoint works.

Each service should provide:

```text
Structured Logs
Metrics
Traces
Health Endpoints
Error Classification
Request Correlation
Graceful Shutdown
```

---

# 97. Definition of Production-Grade for Forge

For Forge, production-grade does not mean:

> “Uses lots of technologies.”

It means the system can demonstrate predictable behavior under:

```text
Failure
Concurrency
Retries
Duplicates
Timeouts
Partial outages
Unauthorized access
Untrusted code
Operational debugging
```

---

# 98. Required Engineering Demonstrations

The finished project should be able to demonstrate:

### Demonstration 1 — Duplicate Webhook

Send the same webhook twice.

Expected:

```text
One build
One deployment
No duplicate side effects
```

### Demonstration 2 — Worker Crash

Kill a build worker during a build.

Expected:

```text
Lease expires
Job recovers
Retry occurs
No corrupted state
```

### Demonstration 3 — Failed Deployment

Deploy an unhealthy image.

Expected:

```text
Health check fails
New container removed
Old version remains live
```

### Demonstration 4 — Rollback

Rollback to a previous artifact.

Expected:

```text
New deployment created
Previous artifact reused
History preserved
```

### Demonstration 5 — Cross-Tenant Access

Attempt to access another organization's project.

Expected:

```text
403 / 404 according to API policy
No data leakage
```

### Demonstration 6 — Control Plane Failure

Stop a Forge control-plane service while an application is running.

Expected:

```text
Customer application continues serving traffic
```

### Demonstration 7 — Kafka Redelivery

Force a consumer to process the same event again.

Expected:

```text
No duplicate business effect
```

---

# 99. Implementation Roadmap

## Phase 0 — Foundation

Build:

- monorepo structure
- service templates
- Docker Compose
- PostgreSQL
- Kafka
- OCI registry
- OpenTelemetry
- Prometheus
- Grafana
- CI
- event contracts
- API contracts
- ADRs

---

## Phase 1 — Identity

Build:

- signup
- login
- sessions/tokens
- personal organization
- organization membership
- RBAC
- authorization middleware

---

## Phase 2 — Control Plane

Build:

- projects
- environments
- GitHub integration
- repository configuration
- webhook validation
- configuration snapshots

---

## Phase 3 — Build

Build:

- build records
- Build Workers
- BuildKit
- Dockerfile builds
- artifact registry
- immutable digests
- build events
- build failure handling

---

## Phase 4 — Deployment

Build:

- deployment state machine
- deployment requests
- Kafka events
- health checks
- deployment concurrency
- safe rollout
- cancellation
- rollback

---

## Phase 5 — Runtime

Build:

- Docker container lifecycle
- runtime state
- routing
- reverse proxy
- runtime health
- customer logs
- request metrics

---

## Phase 6 — Reliability

Implement:

- retries
- idempotency
- leases
- crash recovery
- reconciliation
- failure testing
- duplicate event handling
- timeout policies

---

## Phase 7 — Operations

Implement:

- dashboards
- alerts
- incidents
- audit logs
- retention policies
- load testing
- operational runbooks
- security testing

---

# 100. Definition of Done

Forge is not considered complete merely because the dashboard works.

The system is considered MVP-complete when a developer can:

```text
Create account
      ↓
Create organization
      ↓
Create project
      ↓
Connect GitHub repository
      ↓
Push Dockerfile
      ↓
Trigger build
      ↓
Produce immutable OCI image
      ↓
Deploy
      ↓
Run health checks
      ↓
Switch traffic
      ↓
View deployment
      ↓
View logs
      ↓
View metrics
      ↓
Rollback
```

and the system can demonstrate:

```text
Security
+
Reliability
+
Idempotency
+
Failure Recovery
+
Observability
+
Multi-Tenancy
```

---

# 101. Architecture Success Criteria

The architecture is successful if it can provide evidence for:

- service isolation
- independent service deployment
- Kafka-based asynchronous communication
- reliable event publication
- idempotent consumers
- immutable artifacts
- safe deployments
- deployment rollback
- worker recovery
- distributed tracing
- tenant isolation
- untrusted build isolation
- control-plane/data-plane separation
- measured load-test behavior

---

# 102. Final Architecture

The final target architecture is:

```text
                         ┌──────────────────┐
                         │ React + TypeScript│
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │   API Gateway    │
                         └────────┬─────────┘
                                  │
            ┌─────────────────────┼─────────────────────┐
            │                     │                     │
            ▼                     ▼                     ▼
      ┌───────────┐       ┌──────────────┐       ┌──────────────┐
      │ Identity  │       │ Control Plane│       │ Deployment   │
      │  Service  │       │   Service    │       │   Service    │
      └─────┬─────┘       └──────┬───────┘       └──────┬───────┘
            │                    │                      │
            │                    │                      ▼
            │                    │                ┌────────────┐
            │                    │                │  Runtime   │
            │                    │                │  Service   │
            │                    │                └─────┬──────┘
            │                    │                      │
            │                    │                      ▼
            │                    │                   Docker
            │                    │                      │
            │                    │                      ▼
            │                    │               Reverse Proxy
            │                    │
            └──────────┬─────────┘
                       │
                       ▼
                 ┌───────────┐
                 │ PostgreSQL│
                 └───────────┘

                       ▲
                       │
                 Transactional
                    Outbox
                       │
                       ▼
                 ┌───────────┐
                 │   Kafka   │
                 └─────┬─────┘
                       │
                       ▼
                ┌────────────┐
                │   Build    │
                │   Service  │
                └──────┬─────┘
                       │
                       ▼
                ┌────────────┐
                │Build Worker│
                └──────┬─────┘
                       │
                       ▼
                    BuildKit
                       │
                       ▼
                 OCI Registry


       ┌────────────────────────────────────┐
       │         Observability              │
       │                                    │
       │ OpenTelemetry → Collector          │
       │                    │               │
       │              Prometheus            │
       │                    │               │
       │                 Grafana             │
       └────────────────────────────────────┘
```

---

# 103. Final Principle

Forge should not attempt to **look** like a production platform.

It should attempt to **behave** like one.

The architecture therefore prioritizes:

```text
Real service boundaries
Real failure modes
Real asynchronous communication
Real security boundaries
Real observability
Real recovery mechanisms
Real deployment guarantees
Real measurable performance
```

rather than maximizing the number of technologies used.

The system should evolve based on demonstrated bottlenecks and operational evidence.

**Forge is a distributed system because its responsibilities genuinely benefit from distribution — not because microservices are fashionable.**