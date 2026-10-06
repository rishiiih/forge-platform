package com.forge.controlplane.build;

import com.forge.controlplane.events.BuildRequestedPayload;
import com.forge.controlplane.events.EventEnvelope;
import com.forge.controlplane.outbox.OutboxEvent;
import com.forge.controlplane.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class BuildService {

    private static final String PRODUCER =
            "control-plane-service";

    private static final String EVENT_TYPE =
            "BuildRequested";

    private static final int EVENT_VERSION = 1;

    private final BuildRepository buildRepository;

    private final OutboxRepository outboxRepository;

    private final JsonMapper jsonMapper;

    public BuildService(
            BuildRepository buildRepository,
            OutboxRepository outboxRepository,
            JsonMapper jsonMapper
    ) {
        this.buildRepository = buildRepository;
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public UUID requestBuild(
            UUID organizationId,
            UUID projectId,
            String repository,
            String commitSha,
            String branch
    ) throws JacksonException {

        UUID buildId =
                UUID.randomUUID();

        UUID eventId =
                UUID.randomUUID();

        UUID correlationId =
                UUID.randomUUID();

        Instant now =
                Instant.now();

        Build build =
                new Build(
                        buildId,
                        organizationId,
                        projectId,
                        commitSha,
                        branch,
                        "Dockerfile",
                        ".",
                        BuildStatus.QUEUED,
                        now,
                        now
                );

        buildRepository.insert(build);

        BuildRequestedPayload payload =
                new BuildRequestedPayload(
                        buildId,
                        projectId,
                        repository,
                        commitSha,
                        branch,
                        "Dockerfile",
                        "."
                );

        EventEnvelope<BuildRequestedPayload> envelope =
                new EventEnvelope<>(
                        eventId,
                        EVENT_TYPE,
                        EVENT_VERSION,
                        now,
                        PRODUCER,
                        correlationId,
                        null,
                        organizationId,
                        payload
                );

        String serializedPayload =
                jsonMapper.writeValueAsString(envelope);

        OutboxEvent outboxEvent =
                new OutboxEvent(
                        UUID.randomUUID(),
                        eventId,
                        EVENT_TYPE,
                        EVENT_VERSION,
                        "Build",
                        buildId,
                        organizationId,
                        serializedPayload,
                        now,
                        now,
                        null,
                        0,
                        null,
                        null,
                        null
                );

        outboxRepository.insert(
                outboxEvent
        );

        return buildId;
    }
}