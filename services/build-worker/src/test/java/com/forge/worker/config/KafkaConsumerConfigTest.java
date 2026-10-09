package com.forge.worker.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @SuppressWarnings("unchecked")
    @Test
    void createsErrorHandlerWithKafkaTemplate() {
        KafkaTemplate<String, String> kafkaTemplate =
                mock(KafkaTemplate.class);

        KafkaConsumerConfig config = new KafkaConsumerConfig();

        DefaultErrorHandler errorHandler =
                config.kafkaErrorHandler(kafkaTemplate);

        assertNotNull(errorHandler);
    }
}