package io.kairos.jobservice.controller;

import io.kairos.jobservice.api.ApiResponse;
import io.kairos.jobservice.dto.*;
import io.kairos.jobservice.mapper.JobMapper;
import io.kairos.jobservice.model.Job;
import io.kairos.jobservice.model.TaskExecutionHistory;
import io.kairos.jobservice.model.TaskSchedule;
import io.kairos.jobservice.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping("/{userId}")
    public Mono<ApiResponse<JobResponse>> create(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateJobRequest request) {

        UUID jobId = UUID.randomUUID();

        Job job = JobMapper.toEntity(userId, jobId, request);

        return jobService.createJob(job)
                .map(saved -> JobMapper.toResponse(saved, null))
                .map(ApiResponse::success);
    }

    @GetMapping("/{userId}/{jobId}")
    public Mono<ApiResponse<JobResponse>> get(
            @PathVariable UUID userId,
            @PathVariable UUID jobId) {

        return jobService.getJob(userId, jobId)
                .map(job -> JobMapper.toResponse(job, null))
                .map(ApiResponse::success);
    }

    @GetMapping("/{userId}")
    public Mono<ApiResponse<List<Job>>> getUserJobs(@PathVariable UUID userId) {
        return jobService.getUserJobs(userId)
                .collectList()
                .map(ApiResponse::success);
    }

    @PutMapping
    public Mono<Job> update(@RequestBody Job job) {
        return jobService.updateJob(job);
    }

    @DeleteMapping("/{userId}/{jobId}")
    public Mono<ApiResponse<Void>> delete(
            @PathVariable UUID userId,
            @PathVariable UUID jobId) {

        return jobService.deleteJob(userId, jobId)
                .thenReturn(ApiResponse.success(null));
    }

    @GetMapping("/execution/{jobId}")
    public Flux<TaskExecutionHistory> history(@PathVariable UUID jobId) {
        return jobService.getHistory(jobId);
    }
}