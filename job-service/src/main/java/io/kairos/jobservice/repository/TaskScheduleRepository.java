package io.kairos.jobservice.repository;

import io.kairos.jobservice.model.TaskSchedule;
import io.kairos.jobservice.model.TaskScheduleKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;

public interface TaskScheduleRepository extends ReactiveCassandraRepository<TaskSchedule, TaskScheduleKey> {
}