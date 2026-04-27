package io.kairos.scheduler.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@PrimaryKeyClass
public class TaskScheduleKey {

    @PrimaryKeyColumn(name = "next_execution_time", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private long nextExecutionTime;

    @PrimaryKeyColumn(name = "task_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private String taskId;
}
