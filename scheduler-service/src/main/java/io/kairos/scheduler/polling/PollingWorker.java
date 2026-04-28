package io.kairos.scheduler.polling;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.entity.TaskScheduleKey;
import io.kairos.scheduler.kafka.producer.TaskEventProducer;
import io.kairos.scheduler.hashing.HashRange;
import io.kairos.scheduler.raft.RaftNode;
import io.kairos.scheduler.raft.SchedulerStateMachine;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import io.kairos.scheduler.util.ExecutionIntervalCalculator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
public class PollingWorker {

    private final RaftNode raftNode;
    private final SchedulerStateMachine stateMachine;
    private final TaskScheduleRepository taskScheduleRepository;
    private final TaskEventProducer taskEventProducer;

    @Value("${kairos.raft.node-id:scheduler-1}")
    private String nodeId;

    @Value("${kairos.scheduler.polling-interval-ms:60000}")
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

    @Scheduled(fixedDelayString = "${kairos.scheduler.polling-interval-ms:60000}")
    public void pollAndExecuteTasks() {
        if (isPolling.getAndSet(true)) {
            log.trace("Polling already in progress, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();

            if (isRebalancing()) {
                log.debug("Cluster rebalancing in progress, pausing polling");
                return;
            }

            HashRange myRange = getMyHashRange();
            if (myRange == null) {
                log.warn("No hash range assigned to node {}, skipping poll cycle", nodeId);
                return;
            }

            long nextBucket = (System.currentTimeMillis() / 1000) + 60;

            log.info("Polling — node: {}, bucket: {}, hashRange: [{}, {}]",
                    nodeId, nextBucket, myRange.start(), myRange.end());

            List<TaskSchedule> tasksToExecute = taskScheduleRepository.findTasksByTimeAndHashRange(
                    nextBucket,
                    myRange.start(),
                    myRange.end()
            );

            if (!tasksToExecute.isEmpty()) {
                log.info("Found {} tasks ready for execution in window [{}s]", tasksToExecute.size(), nextBucket);

                List<TaskSchedule> newTasks = tasksToExecute.stream()
                        .filter(task -> !isTaskRecentlyProcessed(task.getKey().getJobId().toString()))
                        .toList();

                if (!newTasks.isEmpty()) {
                    log.debug("Publishing {} new tasks to Kafka", newTasks.size());
                    taskEventProducer.publishTaskExecutionBatch(newTasks)
                            .exceptionally(ex -> {
                                log.error("Failed to publish tasks to Kafka", ex);
                                return null;
                            })
                            .thenRun(() -> {
                                newTasks.forEach(task -> {
                                    try {
                                        // DELETING old task
                                        long executionTime = task.getKey().getNextExecutionTime();
                                        long ringHash = task.getKey().getRingHash();
                                        var jobId = task.getKey().getJobId();

                                        taskScheduleRepository.deleteTask(executionTime, ringHash, jobId);
                                        log.debug("Deleted executed task - jobId: {}, executionTime: {}", jobId, executionTime);

                                        // For recurring jobs, INSERTING new task with next execution time
                                        if (task.getIsRecurring() != null && task.getIsRecurring()) {
                                            long nextExecutionTime = ExecutionIntervalCalculator.calculateNextExecutionTime(
                                                    executionTime,
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

                                            taskScheduleRepository.save(newTask);
                                            log.debug("Scheduled next execution - jobId: {}, nextExecutionTime: {}", jobId, nextExecutionTime);
                                        } else {
                                            log.debug("Task is one-time, no new task scheduled - jobId: {}", jobId);
                                        }

                                        markTaskAsProcessed(jobId.toString());

                                    } catch (Exception e) {
                                        log.error("Error processing task cleanup - jobId: {}", task.getKey().getJobId(), e);
                                    }
                                });
                                log.debug("Completed task processing - deleted {} tasks, scheduled recurring instances", newTasks.size());
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

    private boolean isRebalancing() {
        return stateMachine.getClusterState() != io.kairos.scheduler.raft.ClusterState.ACTIVE;
    }

    private HashRange getMyHashRange() {
        return stateMachine.getMyRange(nodeId);
    }


    private boolean isTaskRecentlyProcessed(String taskId) {
        return recentlyProcessed.contains(taskId);
    }

    private void markTaskAsProcessed(String taskId) {
        recentlyProcessed.add(taskId);

        if (recentlyProcessed.size() > RECENT_WINDOW_SIZE) {
            recentlyProcessed.clear();
        }
    }

    public boolean isActive() {
        return !isPolling.get();
    }
}
