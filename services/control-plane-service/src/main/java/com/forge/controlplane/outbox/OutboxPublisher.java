package com.forge.controlplane.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OutboxPublisher {

    private static final String BUILD_EVENTS_TOPIC =
            "forge.build.events";

    private static final int BATCH_SIZE = 50;

    private static final int LEASE_SECONDS = 30;

    private final OutboxRepository outboxRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final String workerId =
            "control-plane-outbox-" + UUID.randomUUID();

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxRepository.claimUnpublishedEvents(
                        BATCH_SIZE,
                        workerId,
                        LEASE_SECONDS
                );

        for (OutboxEvent event : events) {

            try {

                kafkaTemplate
                        .send(
                                BUILD_EVENTS_TOPIC,
                                event.aggregateId().toString(),
                                event.payload()
                        )
                        .get();

                outboxRepository.markPublished(
                        event.eventId(),
                        workerId
                );

            } catch (Exception exception) {

                String errorMessage =
                        exception.getMessage();

                if (errorMessage == null) {
                    errorMessage =
                            exception.getClass().getSimpleName();
                }

                outboxRepository.recordFailure(
                        event.eventId(),
                        workerId,
                        errorMessage
                );
            }
        }
    }
}