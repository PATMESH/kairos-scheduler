package io.kairos.jobservice.mapper;

import io.kairos.jobservice.dto.*;
import io.kairos.jobservice.model.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;

@Slf4j
public class JobMapper {

    public static Job toEntity(UUID userId, UUID jobId, CreateJobRequest request) {
        return Job.builder()
                .key(new JobKey(userId, jobId))
                .executionInterval(request.getExecutionInterval())
                .payload(request.getPayload())
                .callbackUrl(request.getCallbackUrl())
                .isRecurring(request.isRecurring())
                .maxRetryCount(request.getMaxRetryCount())
                .scheduledAt(request.getScheduledAt())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static JobResponse toResponse(Job job) {
        return JobResponse.builder()
                .jobId(job.getKey().getJobId())
                .userId(job.getKey().getUserId())
                .executionInterval(job.getExecutionInterval())
                .isRecurring(Boolean.TRUE.equals(job.getIsRecurring()))
                .maxRetryCount(job.getMaxRetryCount())
                .callbackUrl(job.getCallbackUrl())
                .createdAt(job.getCreatedAt())
                .scheduledAt(job.getScheduledAt())
                .build();
    }
}