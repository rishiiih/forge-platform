package com.forge.controlplane.events;

import java.util.UUID;

public record BuildRequestedPayload(
        UUID buildId,
        UUID projectId,
        String repository,
        String commitSha,
        String branch,
        String dockerfilePath,
        String buildContext
) {
}