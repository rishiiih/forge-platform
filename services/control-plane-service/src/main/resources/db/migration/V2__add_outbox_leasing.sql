ALTER TABLE outbox_events
ADD COLUMN locked_until TIMESTAMPTZ;

ALTER TABLE outbox_events
ADD COLUMN locked_by VARCHAR(255);

CREATE INDEX idx_outbox_available
    ON outbox_events(created_at)
    WHERE published_at IS NULL
      AND locked_until IS NULL;