package io.kairos.scheduler.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@PrimaryKeyClass
public class TaskScheduleKey implements Serializable {

    @PrimaryKeyColumn(name = "next_execution_time", type = PrimaryKeyType.PARTITIONED)
    private Long nextExecutionTime;

    @PrimaryKeyColumn(name = "ring_hash", type = PrimaryKeyType.CLUSTERED)
    private Long ringHash;

    @PrimaryKeyColumn(name = "job_id", type = PrimaryKeyType.CLUSTERED)
    private java.util.UUID jobId;
}
