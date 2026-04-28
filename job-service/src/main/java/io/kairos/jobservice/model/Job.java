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

    @Column("is_recurring")
    private Boolean isRecurring;

    @Column("max_retry")
    private Integer maxRetry;

    @Column("created_at")
    private Instant createdAt;
}