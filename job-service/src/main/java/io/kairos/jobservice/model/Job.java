package io.kairos.jobservice.model;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.*;
import java.time.Instant;
import java.util.UUID;

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
    private Integer maxRetry;

    @Column("scheduled_at")
    private Instant scheduledAt;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}