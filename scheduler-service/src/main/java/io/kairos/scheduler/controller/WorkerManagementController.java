package io.kairos.scheduler.controller;

import io.kairos.scheduler.service.WorkerManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for monitoring and managing scheduler workers
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workers")
public class WorkerManagementController {

    private final WorkerManagementService workerManagementService;

    public WorkerManagementController(WorkerManagementService workerManagementService) {
        this.workerManagementService = workerManagementService;
    }

    /**
     * Get status of all workers
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getWorkerStatus() {
        try {
            Map<String, Object> status = workerManagementService.getWorkerStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error fetching worker status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get polling worker status
     */
    @GetMapping("/polling/status")
    public ResponseEntity<Map<String, Object>> getPollingWorkerStatus() {
        try {
            Map<String, Object> status = workerManagementService.getPollingWorkerStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error fetching polling worker status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recovery worker status
     */
    @GetMapping("/recovery/status")
    public ResponseEntity<Map<String, Object>> getRecoveryWorkerStatus() {
        try {
            Map<String, Object> status = workerManagementService.getRecoveryWorkerStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error fetching recovery worker status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get system health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        try {
            Map<String, Object> health = workerManagementService.getSystemHealth();
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error fetching system health", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
