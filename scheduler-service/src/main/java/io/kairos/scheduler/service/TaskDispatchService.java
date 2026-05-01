package io.kairos.scheduler.service;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.entity.TaskScheduleKey;
import io.kairos.scheduler.kafka.producer.TaskEventProducer;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import io.kairos.scheduler.util.ExecutionIntervalCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDispatchService {

    private final TaskScheduleRepository repository;
    private final TaskEventProducer producer;

    public void processBatch(List<TaskSchedule> tasks) {
        if (tasks == null || tasks.isEmpty()) return;

        producer.publishTaskExecutionBatch(tasks)
                .exceptionally(ex -> {
                    log.error("Failed to publish tasks batch", ex);
                    return null;
                })
                .thenRun(() -> tasks.forEach(this::postProcess));
    }

    public void processSingle(TaskSchedule task) {
        processBatch(List.of(task));
    }

    private void postProcess(TaskSchedule task) {
        try {
            long executionTime = task.getKey().getNextExecutionTime();
            long ringHash = task.getKey().getRingHash();
            var jobId = task.getKey().getJobId();

            // DELETE THE TASK
            repository.deleteTask(executionTime, ringHash, jobId);
            log.debug("Deleted executed task - jobId: {}, executionTime: {}", jobId, executionTime);

            // RESCHEDULING
            if (Boolean.TRUE.equals(task.getIsRecurring())) {
                long nextExecutionTime = ExecutionIntervalCalculator.calculateNextExecutionTimeFromNow(
                        task.getExecutionInterval()
                );

                TaskScheduleKey newKey = TaskScheduleKey.builder()
                        .nextExecutionTime(nextExecutionTime)
                        .ringHash(ringHash)
                        .jobId(jobId)
                        .build();

                TaskSchedule newTask = TaskSchedule.builder()
                        .key(newKey)
                        .executionInterval(task.getExecutionInterval())
                        .isRecurring(true)
                        .build();

                repository.save(newTask);
                log.debug("Scheduled next execution - jobId: {}, nextExecutionTime: {}", jobId, nextExecutionTime);
            }

        } catch (Exception e) {
            log.error("Post-processing failed - jobId: {}", task.getKey().getJobId(), e);
        }
    }
}
