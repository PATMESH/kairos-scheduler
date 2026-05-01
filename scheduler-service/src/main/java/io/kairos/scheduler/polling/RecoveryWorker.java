package io.kairos.scheduler.polling;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import io.kairos.scheduler.service.TaskDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryWorker {

    private final TaskScheduleRepository taskScheduleRepository;
    private final TaskDispatchService taskDispatchService;

    @Value("${kairos.scheduler.node-id:scheduler-1}")
    private String nodeId;

    @Value("${kairos.scheduler.recovery-lookback-minutes:30}")
    private long recoveryLookbackMinutes;

    private final AtomicBoolean isRecovering = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${kairos.scheduler.recovery-scan-interval-ms:300000}")
    public void scanAndRecoverOrphanedTasks() {
        if (isRecovering.getAndSet(true)) {
            log.debug("Recovery scan already in progress, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            long now = Instant.now().getEpochSecond();
            long lookbackSeconds = recoveryLookbackMinutes * 60;
            long alignedNow = (now / 60) * 60;
            long windowStart = alignedNow - lookbackSeconds;

            log.info("Recovery scan started — node: {}, scanning [{} → {}] ({} min lookback)",
                    nodeId, windowStart, now, recoveryLookbackMinutes);

            int totalFound = 0;
            int totalRecovered = 0;

            for (long bucket = windowStart; bucket <= now; bucket += 60) {
                List<TaskSchedule> orphans = taskScheduleRepository.findTasksByTime(bucket);

                if (orphans.isEmpty()) continue;

                totalFound += orphans.size();
                log.warn("Found {} orphaned tasks in bucket {}", orphans.size(), bucket);

               taskDispatchService.processBatch(orphans);
            }

            long duration = System.currentTimeMillis() - startTime;

            if (totalFound == 0) {
                log.debug("Recovery scan complete — no orphaned tasks found ({}ms)", duration);
            } else {
                log.info("Recovery scan complete — found: {}, recovered: {}, duration: {}ms",
                        totalFound, totalRecovered, duration);
            }

        } catch (Exception e) {
            log.error("Error during recovery scan", e);
        } finally {
            isRecovering.set(false);
        }
    }

    public boolean isActive() {
        return !isRecovering.get();
    }
}