package io.kairos.execution.repo;

import io.kairos.execution.model.TaskExecutionHistory;
import io.kairos.execution.model.TaskExecutionKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;

public interface TaskExecutionHistoryRepository
        extends ReactiveCassandraRepository<TaskExecutionHistory, TaskExecutionKey> {
}