package io.kairos.scheduler.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Calculates the next execution time based on execution interval
 * Supports formats:
 * - Numeric (seconds): "300" → 5 minutes
 * - ISO 8601 Duration: "PT5M" → 5 minutes, "P1D" → 1 day, "PT1H30M" → 1.5 hours
 */
@Slf4j
public class ExecutionIntervalCalculator {

    /**
     * Calculate next execution time
     *
     * @param currentExecutionTime Current execution time in seconds
     * @param executionInterval Interval string (numeric seconds or ISO 8601 duration)
     * @return Next execution time in seconds
     */
    public static long calculateNextExecutionTime(long currentExecutionTime, String executionInterval) {
        if (executionInterval == null || executionInterval.isEmpty()) {
            log.warn("Invalid execution interval: {}", executionInterval);
            return currentExecutionTime; // Default: no change
        }

        try {
            long intervalSeconds;

            if (executionInterval.startsWith("P") || executionInterval.startsWith("PT")) {
                // ISO 8601 Duration format
                Duration duration = Duration.parse(executionInterval);
                intervalSeconds = duration.getSeconds();
            } else {
                // Try to parse as numeric seconds
                intervalSeconds = Long.parseLong(executionInterval);
            }

            return currentExecutionTime + intervalSeconds;

        } catch (Exception e) {
            log.error("Failed to parse execution interval: {}", executionInterval, e);
            return currentExecutionTime; // Default: no change
        }
    }
}
