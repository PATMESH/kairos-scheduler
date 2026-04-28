package io.kairos.jobservice.model;

import lombok.*;
import org.springframework.data.cassandra.core.mapping.*;
import java.util.UUID;

@Table("task_schedule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSchedule {

    @PrimaryKey
    private TaskScheduleKey key;

    @Column("execution_interval")
    private String executionInterval;

    @Column("is_recurring")
    private Boolean isRecurring;
}