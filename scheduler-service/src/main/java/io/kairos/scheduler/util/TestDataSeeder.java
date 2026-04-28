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

    }
}
