package io.kairos.scheduler.service;

import io.kairos.scheduler.polling.PollingWorker;
import io.kairos.scheduler.polling.RecoveryWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to manage and monitor polling and recovery workers
 */
@Slf4j
@Service
public class WorkerManagementService {

    private final PollingWorker pollingWorker;
    private final RecoveryWorker recoveryWorker;

    public WorkerManagementService(PollingWorker pollingWorker, RecoveryWorker recoveryWorker) {
        this.pollingWorker = pollingWorker;
        this.recoveryWorker = recoveryWorker;
        log.info("WorkerManagementService initialized");
    }

    /**
     * Get status of all workers
     */
    public Map<String, Object> getWorkerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("pollingWorker", getPollingWorkerStatus());
        status.put("recoveryWorker", getRecoveryWorkerStatus());
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    /**
     * Get polling worker status
     */
    public Map<String, Object> getPollingWorkerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("name", "PollingWorker");
        status.put("active", pollingWorker.isActive());
        status.put("description", "Continuously polls for scheduled tasks within hash range");
        return status;
    }

    /**
     * Get recovery worker status
     */
    public Map<String, Object> getRecoveryWorkerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("name", "RecoveryWorker");
        status.put("active", recoveryWorker.isActive());
        status.put("description", "Scans for orphaned tasks and triggers recovery");
        return status;
    }

    /**
     * Get overall system health
     */
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("pollingWorkerActive", pollingWorker.isActive());
        health.put("recoveryWorkerActive", recoveryWorker.isActive());
        health.put("systemHealthy", pollingWorker.isActive() && recoveryWorker.isActive());
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }
}
