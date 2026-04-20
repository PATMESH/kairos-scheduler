package io.kairos.jobservice.service;

import io.kairos.jobservice.model.*;
import io.kairos.jobservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final TaskScheduleRepository scheduleRepository;
    private final TaskExecutionHistoryRepository historyRepository;

    public Mono<Job> createJob(Job job) {
        job.setCreatedAt(Instant.now());
        return jobRepository.save(job);
    }

    public Mono<Job> getJob(UUID userId, UUID jobId) {
        return jobRepository.findById(new JobKey(userId, jobId));
    }

    public Flux<Job> getUserJobs(UUID userId) {
        return jobRepository.findByKeyUserId(userId);
    }

    public Mono<Void> deleteJob(UUID userId, UUID jobId) {
        return jobRepository.deleteById(new JobKey(userId, jobId));
    }

    public Mono<Job> updateJob(Job job) {
        return jobRepository.save(job);
    }

    public Mono<TaskSchedule> scheduleTask(TaskSchedule schedule) {
        return scheduleRepository.save(schedule);
    }

    public Mono<TaskExecutionHistory> saveExecution(TaskExecutionHistory history) {
        return historyRepository.save(history);
    }

    public Flux<TaskExecutionHistory> getHistory(UUID jobId) {
        return historyRepository.findByKeyJobId(jobId);
    }
}