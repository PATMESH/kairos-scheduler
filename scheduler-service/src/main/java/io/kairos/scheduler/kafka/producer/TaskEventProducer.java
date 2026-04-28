package io.kairos.scheduler.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.kafka.event.CloudEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class TaskEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kairos.kafka.topic.task-execution:task-execution}")
    private String taskExecutionTopic;

    @Value("${kairos.scheduler.node-id:scheduler-unknown}")
    private String nodeId;

    public TaskEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<Void> publishTaskExecution(TaskSchedule task) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> eventData = buildTaskData(task);

            CloudEvent cloudEvent = CloudEvent.of(
                    "io.kairos.scheduler.task.execution",
                    eventId,
                    "scheduler://" + nodeId,
                    UUID.randomUUID().toString(),
                    eventData
            );

            String eventJson = objectMapper.writeValueAsString(cloudEvent);

            Message<String> message = MessageBuilder
                    .withPayload(eventJson)
                    .setHeader(KafkaHeaders.TOPIC, taskExecutionTopic)
                    .setHeader(KafkaHeaders.KEY, task.getKey().getJobId().toString())
                    .setHeader("ce_type", cloudEvent.getType())
                    .setHeader("ce_id", cloudEvent.getId())
                    .setHeader("ce_source", cloudEvent.getSource())
                    .setHeader("ce_specversion", cloudEvent.getSpecversion())
                    .setHeader("ce_correlationid", cloudEvent.getCorrelationId())
                    .build();

            CompletableFuture<Void> future = new CompletableFuture<>();

            kafkaTemplate.send(message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish task execution event for jobId={}, eventId={}",
                                    task.getKey().getJobId(), eventId, ex);
                            future.completeExceptionally(ex);
                        } else {
                            log.debug("Published task execution event - jobId={}, eventId={}, partition={}",
                                    task.getKey().getJobId(), eventId,
                                    result.getRecordMetadata().partition());
                            future.complete(null);
                        }
                    });

            return future;
        } catch (Exception e) {
            log.error("Error publishing task execution event for jobId={}", task.getKey().getJobId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Void> publishTaskExecutionBatch(java.util.List<TaskSchedule> tasks) {
        return CompletableFuture.allOf(
                tasks.stream()
                        .map(this::publishTaskExecution)
                        .toArray(CompletableFuture[]::new)
        );
    }

    public CompletableFuture<Void> publishOrphanedTaskEvent(TaskSchedule task) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> eventData = buildTaskData(task);
            eventData.put("recovery_reason", "orphaned_job");

            CloudEvent cloudEvent = CloudEvent.of(
                    "io.kairos.scheduler.task.orphaned",
                    eventId,
                    "scheduler://" + nodeId,
                    UUID.randomUUID().toString(),
                    eventData
            );

            String eventJson = objectMapper.writeValueAsString(cloudEvent);

            Message<String> message = MessageBuilder
                    .withPayload(eventJson)
                    .setHeader(KafkaHeaders.TOPIC, taskExecutionTopic)
                    .setHeader(KafkaHeaders.KEY, task.getKey().getJobId().toString())
                    .setHeader("ce_type", cloudEvent.getType())
                    .setHeader("ce_id", cloudEvent.getId())
                    .setHeader("ce_source", cloudEvent.getSource())
                    .build();

            CompletableFuture<Void> future = new CompletableFuture<>();

            kafkaTemplate.send(message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish orphaned task event for jobId={}", task.getKey().getJobId(), ex);
                            future.completeExceptionally(ex);
                        } else {
                            log.info("Published orphaned task event - jobId={}, eventId={}", task.getKey().getJobId(), eventId);
                            future.complete(null);
                        }
                    });

            return future;
        } catch (Exception e) {
            log.error("Error publishing orphaned task event for jobId={}", task.getKey().getJobId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> buildTaskData(TaskSchedule task) {
        Map<String, Object> data = new HashMap<>();
        data.put("jobId", task.getKey().getJobId());
        data.put("nextExecutionTime", task.getKey().getNextExecutionTime());
        data.put("ringHash", task.getKey().getRingHash());
        data.put("executionInterval", task.getExecutionInterval());
        data.put("isRecurring", task.getIsRecurring());
        return data;
    }
}
