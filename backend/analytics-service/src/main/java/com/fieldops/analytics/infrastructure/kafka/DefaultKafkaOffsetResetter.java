package com.fieldops.analytics.infrastructure.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DefaultKafkaOffsetResetter implements KafkaOffsetResetter {

    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaOffsetResetter.class);

    private final KafkaAdmin kafkaAdmin;

    public DefaultKafkaOffsetResetter(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public void resetConsumerGroupToEarliest(String consumerGroup, String topic) {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            TopicDescription desc = adminClient.describeTopics(List.of(topic)).values().get(topic).get(10, TimeUnit.SECONDS);
            List<TopicPartition> partitions = desc.partitions().stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .toList();

            Map<TopicPartition, OffsetSpec> offsetSpecs = partitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliestOffsets =
                    adminClient.listOffsets(offsetSpecs).all().get(10, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetAndMetadata> offsetsToCommit = new HashMap<>();
            for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : earliestOffsets.entrySet()) {
                offsetsToCommit.put(entry.getKey(), new OffsetAndMetadata(entry.getValue().offset()));
            }

            adminClient.alterConsumerGroupOffsets(consumerGroup, offsetsToCommit).all().get(10, TimeUnit.SECONDS);
            log.info("Reset offsets to earliest for consumer group {} on topic {}: {}", consumerGroup, topic, offsetsToCommit);
        } catch (Exception e) {
            log.error("Failed to reset consumer group offsets: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reset consumer group offsets: " + e.getMessage(), e);
        }
    }
}
