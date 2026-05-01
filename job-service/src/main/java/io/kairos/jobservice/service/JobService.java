package io.kairos.jobservice.service;

import io.kairos.jobservice.model.*;
import io.kairos.jobservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.*;

import java.time.Duration;
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

        return jobRepository.save(job)
                .flatMap(saved -> {
                    TaskSchedule schedule = buildTaskSchedule(saved);
                    return scheduleRepository.save(schedule)
                            .thenReturn(saved);
                });
    }

    public Mono<Job> updateJob(Job updatedJob) {
        UUID userId = updatedJob.getKey().getUserId();
        UUID jobId = updatedJob.getKey().getJobId();

        return jobRepository.findById(new JobKey(userId, jobId))
                .flatMap(existing -> {
                    boolean intervalChanged = !existing.getExecutionInterval()
                            .equals(updatedJob.getExecutionInterval());
                    boolean recurringChanged = !existing.getIsRecurring()
                            .equals(updatedJob.getIsRecurring());

                    if (!intervalChanged && !recurringChanged) {
                        // no scheduling-related changed
                        return jobRepository.save(updatedJob);
                    }

                    // scheduling changed so deleting old and inserting new task_schedule entry
                    return jobRepository.save(updatedJob)
                            .flatMap(saved ->
                                    deleteAndReschedule(saved, existing)
                                            .thenReturn(saved)
                            );
                });
    }

    public Mono<Void> deleteJob(UUID userId, UUID jobId) {
        return jobRepository.findById(new JobKey(userId, jobId))
                .flatMap(job -> {
                    // deleting from task_schedule
                    TaskScheduleKey oldKey = buildTaskScheduleKey(job);
                    return scheduleRepository.deleteById(oldKey);
                })
                .then(jobRepository.deleteById(new JobKey(userId, jobId)));
    }


    private Mono<Void> deleteAndReschedule(Job newJob, Job oldJob) {
        TaskScheduleKey oldKey = buildTaskScheduleKey(oldJob);
        TaskSchedule newSchedule = buildTaskSchedule(newJob);

        return scheduleRepository.deleteById(oldKey)
                .then(scheduleRepository.save(newSchedule))
                .then();
    }

    private TaskSchedule buildTaskSchedule(Job job) {
        UUID jobId = job.getKey().getJobId();
        long ringHash = Math.abs(jobId.hashCode()) % 1_000_000L;
        long nextExecutionTime = calculateNextExecutionTime(job.getExecutionInterval());

        TaskScheduleKey key = TaskScheduleKey.builder()
                .nextExecutionTime(nextExecutionTime)
                .ringHash(ringHash)
                .jobId(jobId)
                .build();

        return TaskSchedule.builder()
                .key(key)
                .isRecurring(job.getIsRecurring())
                .executionInterval(job.getExecutionInterval())
                .build();
    }

    private TaskScheduleKey buildTaskScheduleKey(Job job) {
        UUID jobId = job.getKey().getJobId();
        long ringHash = Math.abs(jobId.hashCode()) % 1_000_000L;
        long nextExecutionTime = calculateNextExecutionTime(job.getExecutionInterval());

        return TaskScheduleKey.builder()
                .nextExecutionTime(nextExecutionTime)
                .ringHash(ringHash)
                .jobId(jobId)
                .build();
    }

    private long calculateNextExecutionTime(String interval) {
        Duration duration = Duration.parse(interval);
        long nowSec = Instant.now().getEpochSecond();
        long base = ((nowSec / 60) + 1) * 60;
        long executionTime = base + duration.getSeconds();
        return (executionTime / 60) * 60;
    }

    public Mono<Job> getJob(UUID userId, UUID jobId) {
        return jobRepository.findById(new JobKey(userId, jobId));
    }

    public Flux<Job> getUserJobs(UUID userId) {
        return jobRepository.findByKeyUserId(userId);
    }

    public Mono<TaskExecutionHistory> saveExecution(TaskExecutionHistory history) {
        return historyRepository.save(history);
    }

    public Flux<TaskExecutionHistory> getHistory(UUID jobId) {
        return historyRepository.findByKeyJobId(jobId);
    }
}