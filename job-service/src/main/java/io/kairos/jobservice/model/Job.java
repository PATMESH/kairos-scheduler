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

    @Column("interval")
    private String interval;

    @Column("is_recurring")
    private Boolean isRecurring;

    @Column("max_retry")
    private Integer maxRetry;

    @Column("created_at")
    private Instant createdAt;
}