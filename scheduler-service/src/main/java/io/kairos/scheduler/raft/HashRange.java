package io.kairos.scheduler.raft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record HashRange(int start, int end) {

    public static final int RANGE_MAX = 999_999;

    @JsonCreator
    public HashRange(@JsonProperty("start") int start, @JsonProperty("end") int end) {
        this.start = start;
        this.end = end;
    }

    public boolean contains(int hash) {
        return hash >= start && hash <= end;
    }

    @Override
    public String toString() {
        return "[" + start + ", " + end + "]";
    }
}
