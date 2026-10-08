package com.forge.controlplane.build;

import java.util.UUID;

public record CreateBuildResponse(
        UUID buildId,
        BuildStatus status
) {
}