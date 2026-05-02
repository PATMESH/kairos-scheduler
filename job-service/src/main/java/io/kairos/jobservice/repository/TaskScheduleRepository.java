package io.kairos.jobservice.repository;

import io.kairos.jobservice.model.TaskSchedule;
import io.kairos.jobservice.model.TaskScheduleKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TaskScheduleRepository extends ReactiveCassandraRepository<TaskSchedule, TaskScheduleKey> {
    Mono<TaskSchedule> findFirstByKeyJobId(UUID jobId);
}