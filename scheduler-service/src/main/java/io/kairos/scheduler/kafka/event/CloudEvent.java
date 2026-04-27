package io.kairos.scheduler.kafka.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * CloudEvents format wrapper for Kafka messages
 * Reference: https://cloudevents.io/
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CloudEvent {

    /**
     * Identifies the event type in the reverse-DNS format
     */
    @JsonProperty("type")
    private String type;

    /**
     * Uniquely identifies the event
     */
    @JsonProperty("id")
    private String id;

    /**
     * Describes the subject of the event in the context of the originating origin
     */
    @JsonProperty("source")
    private String source;

    /**
     * The version of the CloudEvents specification which the event uses
     */
    @JsonProperty("specversion")
    private String specversion;

    /**
     * Correlates events that are related to one another
     */
    @JsonProperty("correlationid")
    private String correlationId;

    /**
     * Content-type of the data attribute
     */
    @JsonProperty("datacontenttype")
    private String datacontenttype;

    /**
     * The event payload/data
     */
    @JsonProperty("data")
    private Map<String, Object> data;

    /**
     * Optional timestamp of when the event occurred
     */
    @JsonProperty("time")
    private String time;

    public static CloudEvent of(
            String type,
            String id,
            String source,
            String correlationId,
            Map<String, Object> data) {
        return CloudEvent.builder()
                .type(type)
                .id(id)
                .source(source)
                .specversion("1.0")
                .correlationId(correlationId)
                .datacontenttype("application/json")
                .data(data)
                .time(System.currentTimeMillis() + "")
                .build();
    }
}
