package io.kairos.jobservice.model;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.*;
import java.time.Instant;
import java.util.UUID;

@Table("task_execution_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecutionHistory {

    @PrimaryKey
    private TaskExecutionKey key;

    @Column("status")
    private String status;

    @Column("retry_count")
    private Integer retryCount;

    @Column("error_message")
    private String errorMessage;
}