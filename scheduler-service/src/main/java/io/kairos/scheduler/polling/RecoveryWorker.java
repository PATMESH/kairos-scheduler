package io.kairos.scheduler.polling;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.kafka.producer.TaskEventProducer;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Recovery worker - runs as a separate background process
 * Polls every 5 minutes to scan for orphaned/pending tasks in the last 30 minutes
 * and triggers recovery/retry logic
 */
@Slf4j
@Component
public class RecoveryWorker {

    private final TaskScheduleRepository taskScheduleRepository;
    private final TaskEventProducer taskEventProducer;

    @Value("${kairos.scheduler.node-id:scheduler-1}")
    private String nodeId;

    @Value("${kairos.scheduler.recovery-scan-interval-ms:300000}") // 5 minutes
    private long recoveryIntervalMs;

    @Value("${kairos.scheduler.recovery-lookback-minutes:30}")
    private long recoveryLookbackMinutes;

    private final AtomicBoolean isRecovering = new AtomicBoolean(false);

    public RecoveryWorker(TaskScheduleRepository taskScheduleRepository,
                        TaskEventProducer taskEventProducer) {
        this.taskScheduleRepository = taskScheduleRepository;
        this.taskEventProducer = taskEventProducer;
    }

    /**
     * Recovery scan - runs every 5 minutes
     * Scans for tasks that should have been executed but are still PENDING
     */
    @Scheduled(fixedDelayString = "${kairos.scheduler.recovery-scan-interval-ms:300000}")
    public void scanAndRecoverOrphanedTasks() {
        if (isRecovering.getAndSet(true)) {
            log.debug("Recovery scan already in progress, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Calculate lookback window
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long lookbackSeconds = recoveryLookbackMinutes * 60;
            long windowStartTime = currentTimeSeconds - lookbackSeconds;

            log.info("Recovery scan started - node: {}, lookback: {}min, window: [{}s - {}s]",
                    nodeId, recoveryLookbackMinutes, windowStartTime, currentTimeSeconds);

            // Query for PENDING tasks that are overdue for execution
            List<TaskSchedule> orphanedTasks = taskScheduleRepository.findPendingTasksByTime(currentTimeSeconds);

            if (orphanedTasks.isEmpty()) {
                log.debug("No orphaned tasks found in recovery scan");
                return;
            }

            log.warn("Found {} orphaned/pending tasks that require recovery", orphanedTasks.size());

            // Group orphaned tasks by status
            List<TaskSchedule> expiredTasks = orphanedTasks.stream()
                    .filter(task -> task.getNextExecutionTime() < windowStartTime)
                    .toList();

            List<TaskSchedule> recentlyExpiredTasks = orphanedTasks.stream()
                    .filter(task -> task.getNextExecutionTime() >= windowStartTime && task.getNextExecutionTime() <= currentTimeSeconds)
                    .toList();

            if (!expiredTasks.isEmpty()) {
                log.warn("Found {} tasks expired beyond recovery window", expiredTasks.size());
                handleExpiredTasks(expiredTasks);
            }

            if (!recentlyExpiredTasks.isEmpty()) {
                log.info("Found {} recently expired tasks, attempting recovery", recentlyExpiredTasks.size());
                handleRecentlyExpiredTasks(recentlyExpiredTasks);
            }

            long recoveryDuration = System.currentTimeMillis() - startTime;
            log.info("Recovery scan completed in {}ms - processed {} orphaned tasks",
                    recoveryDuration, orphanedTasks.size());

        } catch (Exception e) {
            log.error("Error during recovery scan", e);
        } finally {
            isRecovering.set(false);
        }
    }

    /**
     * Handle recently expired tasks - attempt to retry them
     */
    private void handleRecentlyExpiredTasks(List<TaskSchedule> tasks) {
        tasks.forEach(task -> {
            if (task.getAttemptCount() < task.getMaxRetries()) {
                log.info("Retrying orphaned task - taskId: {}, jobId: {}, attempt: {}/{}",
                        task.getTaskId(), task.getJobId(), task.getAttemptCount() + 1, task.getMaxRetries());

                // Publish orphaned task event for recovery
                taskEventProducer.publishOrphanedTaskEvent(task)
                        .exceptionally(ex -> {
                            log.error("Failed to publish orphaned task for recovery - taskId: {}", task.getTaskId(), ex);
                            return null;
                        });
            } else {
                log.warn("Orphaned task exceeded max retries - taskId: {}, jobId: {}, attempts: {}",
                        task.getTaskId(), task.getJobId(), task.getAttemptCount());
            }
        });
    }

    /**
     * Handle tasks that have expired beyond recovery window
     */
    private void handleExpiredTasks(List<TaskSchedule> tasks) {
        log.warn("Marking {} tasks as failed due to expiration beyond recovery window", tasks.size());
        tasks.forEach(task -> {
            log.debug("Task expired - taskId: {}, jobId: {}, executionTime: {}",
                    task.getTaskId(), task.getJobId(), task.getNextExecutionTime());

            // Could publish a failure event or mark in database
            // For now, just log the expiration
        });
    }

    /**
     * Get recovery worker status
     */
    public boolean isActive() {
        return !isRecovering.get();
    }
}
