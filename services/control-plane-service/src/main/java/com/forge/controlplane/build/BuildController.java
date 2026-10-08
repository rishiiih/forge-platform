package com.forge.controlplane.build;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/builds")
public class BuildController {

    private final BuildService buildService;

    public BuildController(BuildService buildService) {
        this.buildService = buildService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateBuildResponse createBuild(
            @Valid @RequestBody CreateBuildRequest request
    ) throws Exception {

        var buildId = buildService.requestBuild(
                request.organizationId(),
                request.projectId(),
                request.repository(),
                request.commitSha(),
                request.branch()
        );

        return new CreateBuildResponse(
                buildId,
                BuildStatus.QUEUED
        );
    }
}