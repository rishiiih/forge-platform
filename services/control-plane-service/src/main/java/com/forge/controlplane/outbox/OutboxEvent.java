package com.forge.controlplane.outbox;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        UUID organizationId,
        String payload,
        Instant occurredAt,
        Instant createdAt,
        Instant publishedAt,
        int attemptCount,
        String lastError,
        Instant lockedUntil,
        String lockedBy
) {
}