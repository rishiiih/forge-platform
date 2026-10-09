package com.forge.worker.consumer;

import com.forge.worker.build.BuildRepository;
import com.forge.worker.events.BuildRequestedPayload;
import com.forge.worker.events.EventEnvelope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class BuildRequestedConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(BuildRequestedConsumer.class);

    private static final TypeReference<
            EventEnvelope<BuildRequestedPayload>
            > EVENT_TYPE = new TypeReference<>() {};

    private final JsonMapper jsonMapper;
    private final BuildRepository buildRepository;

    public BuildRequestedConsumer(
            JsonMapper jsonMapper,
            BuildRepository buildRepository
    ) {
        this.jsonMapper = jsonMapper;
        this.buildRepository = buildRepository;
    }

    @KafkaListener(topics = "forge.build.events")
    public void consume(String message) throws Exception {
        EventEnvelope<BuildRequestedPayload> event =
                jsonMapper.readValue(message, EVENT_TYPE);

        if (event == null
                || !"BuildRequested".equals(event.eventType())
                || event.eventVersion() != 1
                || event.payload() == null
                || event.payload().buildId() == null) {
            throw new IllegalArgumentException(
                    "Invalid BuildRequested event"
            );
        }

        BuildRequestedPayload payload = event.payload();

        boolean claimed =
                buildRepository.claimQueuedBuild(payload.buildId());

        if (!claimed) {
            log.info(
                    "Skipping duplicate or non-queued build event: {}",
                    payload.buildId()
            );
            return;
        }

        log.info(
                "Build {} claimed. Repository={}, commit={}, branch={}",
                payload.buildId(),
                payload.repository(),
                payload.commitSha(),
                payload.branch()
        );

        // Docker build execution will be added next.
    }
}
