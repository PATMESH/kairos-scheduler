package io.kairos.jobservice.model;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.*;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@PrimaryKeyClass
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskExecutionKey implements Serializable {

    @PrimaryKeyColumn(name = "job_id", type = PrimaryKeyType.PARTITIONED)
    private UUID jobId;

    @PrimaryKeyColumn(name = "execution_time", type = PrimaryKeyType.CLUSTERED)
    private Instant executionTime;
}