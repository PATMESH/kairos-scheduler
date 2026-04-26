package io.kairos.scheduler.raft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record StateMachineCommand(
        @JsonProperty("type") Type type,
        @JsonProperty("ranges") Map<String, HashRange> ranges,
        @JsonProperty("nodeId") String nodeId,
        @JsonProperty("heartbeatEpochMs") long heartbeatEpochMs
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Type {
        REASSIGNING,
        RANGE_UPDATE,
        HEARTBEAT
    }

    @JsonCreator
    public StateMachineCommand {
        Objects.requireNonNull(type, "type");

        if (type == Type.RANGE_UPDATE) {
            if (ranges == null || ranges.isEmpty()) {
                throw new IllegalArgumentException("RANGE_UPDATE requires a non-empty ranges map");
            }
            ranges.forEach((assignedNodeId, range) -> {
                if (assignedNodeId == null || assignedNodeId.isBlank()) {
                    throw new IllegalArgumentException("Range assignment contains a blank node id");
                }
                Objects.requireNonNull(range, "Range assignment for " + assignedNodeId + " is null");
            });
            ranges = Collections.unmodifiableMap(new LinkedHashMap<>(ranges));
            nodeId = null;
            heartbeatEpochMs = 0L;
        } else if (type == Type.HEARTBEAT) {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("HEARTBEAT requires nodeId");
            }
            if (heartbeatEpochMs <= 0L) {
                throw new IllegalArgumentException("HEARTBEAT requires heartbeatEpochMs");
            }
            ranges = Collections.emptyMap();
        } else {
            ranges = Collections.emptyMap();
            nodeId = null;
            heartbeatEpochMs = 0L;
        }
    }

    public static StateMachineCommand reassigning() {
        return new StateMachineCommand(Type.REASSIGNING, Collections.emptyMap(), null, 0L);
    }

    public static StateMachineCommand rangeUpdate(Map<String, HashRange> ranges) {
        return new StateMachineCommand(Type.RANGE_UPDATE, ranges, null, 0L);
    }

    public static StateMachineCommand heartbeat(String nodeId, long heartbeatEpochMs) {
        return new StateMachineCommand(Type.HEARTBEAT, Collections.emptyMap(), nodeId, heartbeatEpochMs);
    }

    public byte[] toBytes() {
        try {
            return MAPPER.writeValueAsBytes(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize command", e);
        }
    }

    public static StateMachineCommand fromBytes(byte[] bytes) {
        try {
            return MAPPER.readValue(bytes, StateMachineCommand.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize command", e);
        }
    }
}
