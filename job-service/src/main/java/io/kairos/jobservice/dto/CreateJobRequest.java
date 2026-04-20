package io.kairos.jobservice.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateJobRequest {

    @NotBlank(message = "executionInterval is required (e.g. PT3H, PT30S)")
    private String executionInterval;

    private boolean isRecurring;

    @Min(0)
    private int maxRetryCount;

    @NotBlank
    private String callbackUrl;

    private String payload;
}