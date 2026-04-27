package io.kairos.scheduler.util;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.entity.TaskScheduleKey;
import io.kairos.scheduler.repository.TaskScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Test data seeder - populates Cassandra with test tasks
 * Activated with --spring.profiles.active=seeder
 */
@Slf4j
@Component
@Profile("seeder")
public class TestDataSeeder implements CommandLineRunner {

    private final TaskScheduleRepository taskScheduleRepository;

    public TestDataSeeder(TaskScheduleRepository taskScheduleRepository) {
        this.taskScheduleRepository = taskScheduleRepository;
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Starting test data seeding...");
            seedTestTasks();
            log.info("Test data seeding completed successfully");
        } catch (Exception e) {
            log.error("Error during test data seeding", e);
        }
    }

    private void seedTestTasks() {
        long currentTimeSeconds = System.currentTimeMillis() / 1000;
        List<TaskSchedule> testTasks = new ArrayList<>();

        // Create tasks for different hash ranges and time windows
        for (int i = 0; i < 10; i++) {
            TaskSchedule task = createTestTask(
                    i,
                    currentTimeSeconds + i,
                    (i * 100_000_000) % 1_000_000_000
            );
            testTasks.add(task);
            log.info("Created test task: taskId={}, ringHash={}, executionTime={}",
                    task.getTaskId(), task.getRingHash(), task.getNextExecutionTime());
        }

        // Save all tasks
        taskScheduleRepository.saveAll(testTasks);
        log.info("Saved {} test tasks to Cassandra", testTasks.size());
    }

    private TaskSchedule createTestTask(int index, long executionTime, int ringHash) {
        String taskId = "test-task-" + index + "-" + UUID.randomUUID();
        String jobId = "test-job-" + (index % 3);

        TaskScheduleKey key = TaskScheduleKey.builder()
                .nextExecutionTime(executionTime)
                .taskId(taskId)
                .build();

        return TaskSchedule.builder()
                .id(key)
                .nextExecutionTime(executionTime)
                .taskId(taskId)
                .jobId(jobId)
                .ringHash(ringHash)
                .status("PENDING")
                .correlationId(UUID.randomUUID().toString())
                .payload("{\"action\": \"send_notification\", \"message\": \"Test task " + index + "\"}")
                .source("test-seeder")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .attemptCount(0)
                .maxRetries(3)
                .metadata(Map.of(
                        "testData", "true",
                        "taskIndex", String.valueOf(index),
                        "environment", "development"
                ))
                .build();
    }
}
