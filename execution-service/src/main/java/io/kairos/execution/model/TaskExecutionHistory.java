package io.kairos.execution.model;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.*;

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
    private int retryCount;

    @Column("error_message")
    private String errorMessage;
}