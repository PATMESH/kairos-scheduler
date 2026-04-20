package io.kairos.jobservice.repository;

import io.kairos.jobservice.model.TaskExecutionHistory;
import io.kairos.jobservice.model.TaskExecutionKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface TaskExecutionHistoryRepository extends ReactiveCassandraRepository<TaskExecutionHistory, TaskExecutionKey> {
    Flux<TaskExecutionHistory> findByKeyJobId(UUID jobId);
}