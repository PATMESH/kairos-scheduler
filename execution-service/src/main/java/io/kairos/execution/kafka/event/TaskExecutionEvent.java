package io.kairos.execution.kafka.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskExecutionEvent {

    private String type;
    private String id;
    private String source;
    private String specversion;
    private String correlationId;
    private String datacontenttype;
    private String time;

    private TaskExecution data;
}
