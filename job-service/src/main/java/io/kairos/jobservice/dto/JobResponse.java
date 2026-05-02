package io.kairos.jobservice.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobResponse {

    private UUID jobId;
    private UUID userId;
    private String executionInterval;
    private boolean isRecurring;
    private int maxRetryCount;
    private String callbackUrl;
    private Instant createdAt;
    private Instant scheduledAt;
}