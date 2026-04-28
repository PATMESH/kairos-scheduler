package io.kairos.scheduler.raft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record HashRange(long start, long end) {

    public static final long RANGE_MAX = 999_999_999_999L;

    @JsonCreator
    public HashRange(@JsonProperty("start") long start, @JsonProperty("end") long end) {
        this.start = start;
        this.end = end;
    }

    public boolean contains(long hash) {
        return hash >= start && hash <= end;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }
}
