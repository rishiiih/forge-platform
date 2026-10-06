package com.forge.controlplane.build;

import java.time.Instant;
import java.util.UUID;

public record Build(
        UUID id,
        UUID organizationId,
        UUID projectId, 
        String commitSha,
        String branch,
        String dockerfilePath,
        String buildContext,
        BuildStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}