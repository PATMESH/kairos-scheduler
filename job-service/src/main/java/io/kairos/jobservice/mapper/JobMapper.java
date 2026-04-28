package io.kairos.jobservice.mapper;

import io.kairos.jobservice.dto.*;
import io.kairos.jobservice.model.*;
import java.time.Instant;
import java.util.UUID;

public class JobMapper {

    public static Job toEntity(UUID userId, UUID jobId, CreateJobRequest request) {
        return Job.builder()
                .key(new JobKey(userId, jobId))
                .executionInterval(request.getExecutionInterval())
                .isRecurring(request.isRecurring())
                .maxRetry(request.getMaxRetryCount())
                .createdAt(Instant.now())
                .build();
    }

    public static JobResponse toResponse(Job job, String nextExecutionTime, String callbackUrl) {
        return JobResponse.builder()
                .jobId(job.getKey().getJobId())
                .userId(job.getKey().getUserId())
                .executionInterval(job.getExecutionInterval())
                .isRecurring(Boolean.TRUE.equals(job.getIsRecurring()))
                .maxRetryCount(job.getMaxRetry())
                .callbackUrl(callbackUrl)
                .createdAt(job.getCreatedAt())
                .nextExecutionTime(nextExecutionTime)
                .build();
    }
}