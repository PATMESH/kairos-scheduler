package io.kairos.authservice.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudEvent<T> {
    private String id;
    private String source;
    private String specVersion;
    private String type;
    private String dataContentType;
    private Instant time;
    private String correlationId;
    private T data;
}
