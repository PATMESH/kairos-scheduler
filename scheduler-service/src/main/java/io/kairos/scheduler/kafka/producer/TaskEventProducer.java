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

    /**
     * Publishes a task execution event to Kafka using CloudEvents format
     */
    public CompletableFuture<Void> publishTaskExecution(TaskSchedule task) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> eventData = buildTaskData(task);

            CloudEvent cloudEvent = CloudEvent.of(
                    "io.kairos.scheduler.task.execution",
                    eventId,
                    "scheduler://" + nodeId,
                    task.getCorrelationId() != null ? task.getCorrelationId() : UUID.randomUUID().toString(),
                    eventData
            );

            String eventJson = objectMapper.writeValueAsString(cloudEvent);

            Message<String> message = MessageBuilder
                    .withPayload(eventJson)
                    .setHeader(KafkaHeaders.TOPIC, taskExecutionTopic)
                    .setHeader(KafkaHeaders.MESSAGE_KEY, task.getJobId())
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
                            log.error("Failed to publish task execution event for taskId={}, jobId={}, eventId={}",
                                    task.getTaskId(), task.getJobId(), eventId, ex);
                            future.completeExceptionally(ex);
                        } else {
                            log.debug("Published task execution event - taskId={}, jobId={}, eventId={}, partition={}",
                                    task.getTaskId(), task.getJobId(), eventId,
                                    result.getRecordMetadata().partition());
                            future.complete(null);
                        }
                    });

            return future;
        } catch (Exception e) {
            log.error("Error publishing task execution event for taskId={}", task.getTaskId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publishes a batch of task execution events
     */
    public CompletableFuture<Void> publishTaskExecutionBatch(java.util.List<TaskSchedule> tasks) {
        return CompletableFuture.allOf(
                tasks.stream()
                        .map(this::publishTaskExecution)
                        .toArray(CompletableFuture[]::new)
        );
    }

    /**
     * Publishes an orphaned task recovery event
     */
    public CompletableFuture<Void> publishOrphanedTaskEvent(TaskSchedule task) {
        try {
            String eventId = UUID.randomUUID().toString();
            Map<String, Object> eventData = buildTaskData(task);
            eventData.put("recovery_reason", "orphaned_job");

            CloudEvent cloudEvent = CloudEvent.of(
                    "io.kairos.scheduler.task.orphaned",
                    eventId,
                    "scheduler://" + nodeId,
                    task.getCorrelationId() != null ? task.getCorrelationId() : UUID.randomUUID().toString(),
                    eventData
            );

            String eventJson = objectMapper.writeValueAsString(cloudEvent);

            Message<String> message = MessageBuilder
                    .withPayload(eventJson)
                    .setHeader(KafkaHeaders.TOPIC, taskExecutionTopic)
                    .setHeader(KafkaHeaders.MESSAGE_KEY, task.getJobId())
                    .setHeader("ce_type", cloudEvent.getType())
                    .setHeader("ce_id", cloudEvent.getId())
                    .setHeader("ce_source", cloudEvent.getSource())
                    .build();

            CompletableFuture<Void> future = new CompletableFuture<>();

            kafkaTemplate.send(message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish orphaned task event for taskId={}", task.getTaskId(), ex);
                            future.completeExceptionally(ex);
                        } else {
                            log.info("Published orphaned task event - taskId={}, eventId={}", task.getTaskId(), eventId);
                            future.complete(null);
                        }
                    });

            return future;
        } catch (Exception e) {
            log.error("Error publishing orphaned task event for taskId={}", task.getTaskId(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private Map<String, Object> buildTaskData(TaskSchedule task) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getTaskId());
        data.put("jobId", task.getJobId());
        data.put("status", task.getStatus());
        data.put("nextExecutionTime", task.getNextExecutionTime());
        data.put("ringHash", task.getRingHash());
        data.put("payload", task.getPayload());
        data.put("attemptCount", task.getAttemptCount());
        data.put("maxRetries", task.getMaxRetries());
        data.put("metadata", task.getMetadata());
        data.put("createdAt", task.getCreatedAt());
        data.put("updatedAt", task.getUpdatedAt());
        return data;
    }
}
