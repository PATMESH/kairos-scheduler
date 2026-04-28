package io.kairos.jobservice.model;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;

import java.io.Serializable;

@PrimaryKeyClass
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskScheduleKey implements Serializable {

    @PrimaryKeyColumn(name = "next_execution_time", type = PrimaryKeyType.PARTITIONED)
    private Long nextExecutionTime;

    @PrimaryKeyColumn(name = "ring_hash", type = PrimaryKeyType.CLUSTERED)
    private Long ringHash;

    @PrimaryKeyColumn(name = "job_id", type = PrimaryKeyType.CLUSTERED)
    private java.util.UUID jobId;
}