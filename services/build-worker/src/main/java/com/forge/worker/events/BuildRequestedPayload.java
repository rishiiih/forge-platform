package com.forge.worker.events;

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
