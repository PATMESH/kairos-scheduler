package io.kairos.execution.service;

import io.kairos.execution.kafka.event.TaskExecution;
import io.kairos.execution.model.Job;
import io.kairos.execution.model.TaskExecutionHistory;
import io.kairos.execution.model.TaskExecutionKey;
import io.kairos.execution.repo.JobRepository;
import io.kairos.execution.repo.TaskExecutionHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final JobRepository jobRepository;
    private final TaskExecutionHistoryRepository historyRepository;

    private final WebClient webClient = WebClient.create();

    public void execute(TaskExecution event) {

        Job job = jobRepository.findByKeyJobId(event.getJobId()).block();

        if (job == null) {
            log.error("Job not found jobId={}", event.getJobId());
            return;
        }

        Instant executionTime = Instant.now();

        try {
            webClient.post()
                    .uri(job.getCallbackUrl())
                    .bodyValue(job.getPayload())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            saveHistory(job.getKey().getJobId(), executionTime,
                    "SUCCESS", job.getMaxRetryCount(), null);

        } catch (Exception e) {

            saveHistory(job.getKey().getJobId(), executionTime,
                    "FAILED", job.getMaxRetryCount(), e.getMessage());
        }
    }

    private void saveHistory(UUID jobId,
                             Instant executionTime,
                             String status,
                             int retryCount,
                             String error) {

        TaskExecutionHistory history = TaskExecutionHistory.builder()
                .key(new TaskExecutionKey(jobId, executionTime))
                .status(status)
                .retryCount(retryCount)
                .errorMessage(error)
                .build();

        historyRepository.save(history)
                .doOnError(e -> log.error("History save failed", e))
                .subscribe();
    }
}