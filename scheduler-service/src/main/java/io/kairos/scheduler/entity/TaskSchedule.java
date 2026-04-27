package io.kairos.scheduler.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("task_schedule")
public class TaskSchedule {

    @PrimaryKey
    private TaskScheduleKey id;

    @Column("next_execution_time")
    private long nextExecutionTime;

    @Column("status")
    private String status; // PENDING, RUNNING, COMPLETED, FAILED

    @Column("ring_hash")
    private int ringHash;

    @Column("job_id")
    private String jobId;

    @Column("task_id")
    private String taskId;

    @Column("correlation_id")
    private String correlationId;

    @Column("payload")
    private String payload;

    @Column("source")
    private String source;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("attempt_count")
    private int attemptCount;

    @Column("max_retries")
    private int maxRetries;

    @Column("metadata")
    private Map<String, String> metadata;
}
