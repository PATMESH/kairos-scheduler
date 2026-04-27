package io.kairos.scheduler.polling;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.kafka.producer.TaskEventProducer;
import io.kairos.scheduler.hashing.HashRange;
import io.kairos.scheduler.raft.RaftNode;
import io.kairos.scheduler.raft.SchedulerStateMachine;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main polling worker that continuously polls for scheduled tasks
 * within its allocated hash range and publishes them to Kafka
 */
@Slf4j
@Component
public class PollingWorker {

    private final RaftNode raftNode;
    private final SchedulerStateMachine stateMachine;
    private final TaskScheduleRepository taskScheduleRepository;
    private final TaskEventProducer taskEventProducer;

    @Value("${kairos.raft.node-id:scheduler-1}")
    private String nodeId;

    @Value("${kairos.scheduler.polling-interval-ms:1000}")
    private long pollingIntervalMs;

    @Value("${kairos.scheduler.time-window-seconds:60}")
    private long timeWindowSeconds;

    private final AtomicBoolean isPolling = new AtomicBoolean(false);
    private final Set<String> recentlyProcessed = new HashSet<>();
    private static final int RECENT_WINDOW_SIZE = 10000;

    public PollingWorker(RaftNode raftNode,
                        SchedulerStateMachine stateMachine,
                        TaskScheduleRepository taskScheduleRepository,
                        TaskEventProducer taskEventProducer) {
        this.raftNode = raftNode;
        this.stateMachine = stateMachine;
        this.taskScheduleRepository = taskScheduleRepository;
        this.taskEventProducer = taskEventProducer;
    }

    /**
     * Main polling loop - runs continuously at fixed intervals
     */
    @Scheduled(fixedDelayString = "${kairos.scheduler.polling-interval-ms:1000}")
    public void pollAndExecuteTasks() {
        if (isPolling.getAndSet(true)) {
            log.trace("Polling already in progress, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            // Check if rebalancing is in progress - if so, pause polling
            if (isRebalancing()) {
                log.debug("Cluster rebalancing in progress, pausing polling");
                return;
            }

            // Get current node's hash range
            HashRange myRange = getMyHashRange();
            if (myRange == null) {
                log.warn("No hash range assigned to node {}, skipping poll cycle", nodeId);
                return;
            }

            // Calculate current execution window
            long currentTimeSeconds = System.currentTimeMillis() / 1000;
            long windowEndTime = currentTimeSeconds + timeWindowSeconds;

            log.trace("Polling cycle started - node: {}, hashRange: [{}, {}], window: [{}s, {}s]",
                    nodeId, myRange.start(), myRange.end(), currentTimeSeconds, windowEndTime);

            // Query tasks in current time window within this node's hash range
            List<TaskSchedule> tasksToExecute = taskScheduleRepository.findTasksByTimeAndHashRange(
                    currentTimeSeconds,
                    myRange.start(),
                    myRange.end()
            );

            if (!tasksToExecute.isEmpty()) {
                log.info("Found {} tasks ready for execution in window [{}s]", tasksToExecute.size(), currentTimeSeconds);

                // Filter out already processed tasks
                List<TaskSchedule> newTasks = tasksToExecute.stream()
                        .filter(task -> !isTaskRecentlyProcessed(task.getTaskId()))
                        .toList();

                if (!newTasks.isEmpty()) {
                    log.debug("Publishing {} new tasks to Kafka", newTasks.size());
                    taskEventProducer.publishTaskExecutionBatch(newTasks)
                            .exceptionally(ex -> {
                                log.error("Failed to publish tasks to Kafka", ex);
                                return null;
                            })
                            .thenRun(() -> {
                                // Mark tasks as processed after successful publish
                                newTasks.forEach(task -> markTaskAsProcessed(task.getTaskId()));
                                log.debug("Marked {} tasks as processed", newTasks.size());
                            });
                } else {
                    log.trace("All {} found tasks were already processed in this cycle", tasksToExecute.size());
                }
            }

            long pollDuration = System.currentTimeMillis() - startTime;
            log.trace("Polling cycle completed in {}ms", pollDuration);

        } catch (Exception e) {
            log.error("Error during polling cycle", e);
        } finally {
            isPolling.set(false);
        }
    }

    /**
     * Check if cluster is currently rebalancing
     */
    private boolean isRebalancing() {
        return stateMachine.getClusterState() != io.kairos.scheduler.raft.ClusterState.ACTIVE;
    }

    /**
     * Get this node's assigned hash range
     */
    private HashRange getMyHashRange() {
        return stateMachine.getMyRange(nodeId);
    }

    /**
     * Check if task was recently processed to avoid duplicates
     */
    private boolean isTaskRecentlyProcessed(String taskId) {
        return recentlyProcessed.contains(taskId);
    }

    /**
     * Mark task as processed in this cycle
     */
    private void markTaskAsProcessed(String taskId) {
        recentlyProcessed.add(taskId);

        // Prevent memory unbounded growth
        if (recentlyProcessed.size() > RECENT_WINDOW_SIZE) {
            recentlyProcessed.clear();
        }
    }

    /**
     * Get polling metrics for monitoring
     */
    public boolean isActive() {
        return !isPolling.get();
    }
}
