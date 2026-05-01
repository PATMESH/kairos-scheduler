package io.kairos.scheduler.polling;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.hashing.HashRange;
import io.kairos.scheduler.raft.SchedulerStateMachine;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import io.kairos.scheduler.service.TaskDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
@RequiredArgsConstructor
public class PollingWorker {

    private final SchedulerStateMachine stateMachine;
    private final TaskScheduleRepository taskScheduleRepository;
    private final TaskDispatchService taskDispatchService;

    @Value("${kairos.raft.node-id:scheduler-1}")
    private String nodeId;

    @Value("${kairos.scheduler.polling-interval-ms:60000}")
    private long pollingIntervalMs;

    @Value("${kairos.scheduler.time-window-seconds:60}")
    private long timeWindowSeconds;

    private final AtomicBoolean isPolling = new AtomicBoolean(false);

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

            long nowSec = System.currentTimeMillis() / 1000;
            long nextBucket = ((nowSec / 60) + 1) * 60;

            log.debug("Polling — node: {}, bucket: {}, hashRange: [{}, {}]",
                    nodeId, nextBucket, myRange.start(), myRange.end());

            List<TaskSchedule> tasksToExecute = taskScheduleRepository.findTasksByTimeAndHashRange(
                    nextBucket,
                    myRange.start(),
                    myRange.end()
            );

            if (!tasksToExecute.isEmpty()) {
                log.info("Found {} tasks ready for execution in window [{}s]", tasksToExecute.size(), nextBucket);
                taskDispatchService.processBatch(tasksToExecute);
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

    public boolean isActive() {
        return !isPolling.get();
    }
}
