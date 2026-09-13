package com.fieldops.analytics.infrastructure.kafka;

public interface KafkaOffsetResetter {

    void resetConsumerGroupToEarliest(String consumerGroup, String topic);
}
