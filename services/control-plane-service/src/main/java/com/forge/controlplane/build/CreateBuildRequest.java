package com.forge.controlplane.build;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBuildRequest(

        @NotNull
        UUID organizationId,

        @NotNull
        UUID projectId,

        @NotBlank
        String repository,

        @NotBlank
        String commitSha,

        @NotBlank
        String branch
) {
}