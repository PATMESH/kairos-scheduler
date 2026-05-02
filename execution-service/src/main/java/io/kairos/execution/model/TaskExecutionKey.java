package io.kairos.execution.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@PrimaryKeyClass
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionKey implements Serializable {

    @PrimaryKeyColumn(name = "job_id", type = PrimaryKeyType.PARTITIONED)
    private UUID jobId;

    @PrimaryKeyColumn(name = "execution_time", type = PrimaryKeyType.CLUSTERED)
    private Instant executionTime;
}
