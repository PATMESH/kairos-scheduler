package io.kairos.scheduler.repository;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.entity.TaskScheduleKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskScheduleRepository extends CassandraRepository<TaskSchedule, TaskScheduleKey> {

    /**
     * Query tasks scheduled for a specific time within a hash range
     */
    @Query(value = "SELECT * FROM task_schedule WHERE next_execution_time = ?0 AND ring_hash >= ?1 AND ring_hash < ?2 ALLOW FILTERING")
    List<TaskSchedule> findTasksByTimeAndHashRange(long executionTime, int hashStart, int hashEnd);

    /**
     * Query tasks with PENDING status that are due for execution (within time window)
     */
    @Query(value = "SELECT * FROM task_schedule WHERE next_execution_time <= ?0 AND status = 'PENDING' ALLOW FILTERING")
    List<TaskSchedule> findPendingTasksByTime(long executionTime);

    /**
     * Query tasks for a specific time window
     */
    @Query(value = "SELECT * FROM task_schedule WHERE next_execution_time = ?0 ALLOW FILTERING")
    List<TaskSchedule> findTasksByTime(long executionTime);
}
