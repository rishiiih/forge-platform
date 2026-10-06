CREATE TABLE builds (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    project_id UUID NOT NULL,

    commit_sha VARCHAR(64) NOT NULL,
    branch VARCHAR(255) NOT NULL,

    dockerfile_path VARCHAR(500) NOT NULL,
    build_context VARCHAR(500) NOT NULL,

    status VARCHAR(32) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_builds_project_id
    ON builds(project_id);

CREATE INDEX idx_builds_organization_id
    ON builds(organization_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,

    event_id UUID NOT NULL UNIQUE,

    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL,

    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,

    organization_id UUID,

    payload JSONB NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    published_at TIMESTAMPTZ,

    attempt_count INTEGER NOT NULL DEFAULT 0,

    last_error TEXT
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_events(created_at)
    WHERE published_at IS NULL;