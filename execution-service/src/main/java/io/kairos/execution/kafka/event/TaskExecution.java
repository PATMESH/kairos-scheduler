package io.kairos.execution.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskExecution {
    private Long nextExecutionTime;
    private Long ringHash;
    private UUID jobId;
    private String executionInterval;
    private Boolean isRecurring;
}
