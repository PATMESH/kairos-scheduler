package io.kairos.execution.kafka.consumer;

import io.kairos.execution.kafka.event.TaskExecutionEvent;
import io.kairos.execution.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskExecutionConsumer {

    private final TaskExecutionService executionService;

    @KafkaListener(
            topics = "${kairos.kafka.topic.task-execution}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TaskExecutionEvent event, Acknowledgment ack) {

        try {
            log.info("Consuming task jobId={}", event.getData().getJobId());

            executionService.execute(event.getData());

            ack.acknowledge();

            log.info("Executed task jobId={}", event.getData().getJobId());

        } catch (Exception e) {
            log.error("Execution failed jobId={}", event.getData().getJobId(), e);
        }
    }
}