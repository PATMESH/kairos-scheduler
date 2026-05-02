package io.kairos.execution.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Table("jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @PrimaryKey
    private JobKey key;

    @Column("execution_interval")
    private String executionInterval;

    @Column("payload")
    private String payload;

    @Column("callback_url")
    private String callbackUrl;

    @Column("is_recurring")
    private Boolean isRecurring;

    @Column("max_retry_count")
    private Integer maxRetryCount;

    @Column("scheduled_at")
    private Instant scheduledAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}