package io.kairos.scheduler.util;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class ExecutionIntervalCalculator {

    public static long calculateNextExecutionTime(long currentExecutionTime, String executionInterval) {
        long intervalSeconds = parseIntervalSeconds(executionInterval);
        return alignToMinute(currentExecutionTime + intervalSeconds);
    }

    public static long calculateNextExecutionTimeFromNow(String executionInterval) {
        long intervalSeconds = parseIntervalSeconds(executionInterval);

        long nowSec = System.currentTimeMillis() / 1000;

        long base = ((nowSec / 60) + 1) * 60;

        return alignToMinute(base + intervalSeconds);
    }

    private static long parseIntervalSeconds(String executionInterval) {
        if (executionInterval == null || executionInterval.isEmpty()) {
            log.warn("Invalid execution interval: {}", executionInterval);
            return 0;
        }

        try {
            if (executionInterval.startsWith("P")) {
                return Duration.parse(executionInterval).getSeconds();
            } else {
                return Long.parseLong(executionInterval);
            }
        } catch (Exception e) {
            log.error("Failed to parse execution interval: {}", executionInterval, e);
            return 0;
        }
    }

    private static long alignToMinute(long epochSeconds) {
        return (epochSeconds / 60) * 60;
    }
}