package io.kairos.scheduler.repository;

import io.kairos.scheduler.entity.TaskSchedule;
import io.kairos.scheduler.entity.TaskScheduleKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskScheduleRepository extends CassandraRepository<TaskSchedule, TaskScheduleKey> {

    @Query(value = "SELECT * FROM task_schedule WHERE next_execution_time = ?0 AND ring_hash >= ?1 AND ring_hash < ?2")
    List<TaskSchedule> findTasksByTimeAndHashRange(long executionTime, long hashStart, long hashEnd);

    @Query(value = "SELECT * FROM task_schedule WHERE next_execution_time = ?0")
    List<TaskSchedule> findTasksByTime(long executionTime);

    @Query(value = "DELETE FROM task_schedule WHERE next_execution_time = ?0 AND ring_hash = ?1 AND job_id = ?2")
    void deleteTask(long nextExecutionTime, long ringHash, java.util.UUID jobId);
}
