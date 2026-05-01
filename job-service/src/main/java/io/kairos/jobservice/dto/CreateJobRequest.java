package io.kairos.jobservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;

@Data
public class CreateJobRequest {

    @NotBlank(message = "executionInterval is required (e.g. PT3H, PT30S)")
    private String executionInterval;

    private boolean recurring;

    @Min(0)
    private int maxRetryCount;

    @NotBlank
    private String callbackUrl;

    private String payload;

    private Instant scheduledAt;
}