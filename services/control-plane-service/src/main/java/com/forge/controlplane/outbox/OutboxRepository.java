package com.forge.controlplane.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(OutboxEvent event) {

        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    id,
                    event_id,
                    event_type,
                    event_version,
                    aggregate_type,
                    aggregate_id,
                    organization_id,
                    payload,
                    occurred_at,
                    created_at,
                    attempt_count,
                    locked_until,
                    locked_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?)
                """,
                event.id(),
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                event.aggregateType(),
                event.aggregateId(),
                event.organizationId(),
                event.payload(),
                Timestamp.from(event.occurredAt()),
                Timestamp.from(event.createdAt()),
                event.attemptCount(),
                event.lockedUntil() == null
                        ? null
                        : Timestamp.from(event.lockedUntil()),
                event.lockedBy()
        );
    }

    @Transactional
    public List<OutboxEvent> claimUnpublishedEvents(
            int limit,
            String workerId,
            int leaseSeconds
    ) {

        String sql =
                """
                WITH candidates AS (
                    SELECT id
                    FROM outbox_events
                    WHERE published_at IS NULL
                      AND (
                          locked_until IS NULL
                          OR locked_until < NOW()
                      )
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_events AS events
                SET
                    locked_until = NOW() + (? * INTERVAL '1 second'),
                    locked_by = ?
                FROM candidates
                WHERE events.id = candidates.id
                RETURNING
                    events.id,
                    events.event_id,
                    events.event_type,
                    events.event_version,
                    events.aggregate_type,
                    events.aggregate_id,
                    events.organization_id,
                    events.payload::text,
                    events.occurred_at,
                    events.created_at,
                    events.published_at,
                    events.attempt_count,
                    events.last_error,
                    events.locked_until,
                    events.locked_by
                """;

        return jdbcTemplate.query(
                sql,
                this::mapRow,
                limit,
                leaseSeconds,
                workerId
        );
    }

    public void markPublished(
            UUID eventId,
            String workerId
    ) {

        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET
                    published_at = NOW(),
                    locked_until = NULL,
                    locked_by = NULL,
                    last_error = NULL
                WHERE event_id = ?
                  AND published_at IS NULL
                  AND locked_by = ?
                """,
                eventId,
                workerId
        );
    }

    public void recordFailure(
            UUID eventId,
            String workerId,
            String errorMessage
    ) {

        jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET
                    attempt_count = attempt_count + 1,
                    last_error = ?,
                    locked_until = NULL,
                    locked_by = ?
                WHERE event_id = ?
                  AND published_at IS NULL
                  AND locked_by = ?
                """,
                errorMessage,
                null,
                eventId,
                workerId
        );
    }

    private OutboxEvent mapRow(
            ResultSet resultSet,
            int rowNumber
    ) throws java.sql.SQLException {

        Timestamp publishedAt =
                resultSet.getTimestamp("published_at");

        Timestamp lockedUntil =
                resultSet.getTimestamp("locked_until");

        return new OutboxEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("event_version"),
                resultSet.getString("aggregate_type"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("payload"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                publishedAt == null
                        ? null
                        : publishedAt.toInstant(),
                resultSet.getInt("attempt_count"),
                resultSet.getString("last_error"),
                lockedUntil == null
                        ? null
                        : lockedUntil.toInstant(),
                resultSet.getString("locked_by")
        );
    }
}