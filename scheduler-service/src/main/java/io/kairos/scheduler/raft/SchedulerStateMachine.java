package io.kairos.scheduler.raft;

import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.proto.RaftProtos.LogEntryProto;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.storage.RaftStorage;
import org.apache.ratis.statemachine.TransactionContext;
import org.apache.ratis.statemachine.impl.BaseStateMachine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class SchedulerStateMachine extends BaseStateMachine {

    private final ConcurrentHashMap<String, HashRange> rangeAssignments = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> workerHeartbeats = new ConcurrentHashMap<>();
    private final Object stateLock = new Object();

    private final AtomicReference<ClusterState> clusterState = new AtomicReference<>(ClusterState.INITIALIZING);

    @Value("${kairos.raft.node-id:unknown}")
    private String nodeId;

    @Override
    public void initialize(RaftServer server, RaftGroupId groupId, RaftStorage storage) throws IOException {
        super.initialize(server, groupId, storage);
        log.info("[StateMachine:{}] Initialized for group {}", nodeId, groupId);
    }

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        LogEntryProto entry = trx.getLogEntry();
        try {
            byte[] content = entry.getStateMachineLogEntry().getLogData().toByteArray();
            StateMachineCommand cmd = StateMachineCommand.fromBytes(content);

            log.info("[StateMachine:{}] Applying {} at term={}, index={}, payloadBytes={}",
                    nodeId, cmd.type(), entry.getTerm(), entry.getIndex(), content.length);

            switch (cmd.type()) {
                case REASSIGNING -> {
                    clusterState.set(ClusterState.REASSIGNING);
                    log.info("[StateMachine:{}] Applied REASSIGNING at index={} - polling paused",
                            nodeId, entry.getIndex());
                }
                case RANGE_UPDATE -> {
                    synchronized (stateLock) {
                        rangeAssignments.clear();
                        rangeAssignments.putAll(cmd.ranges());
                    }
                    clusterState.set(ClusterState.ACTIVE);
                    log.info("[StateMachine:{}] Applied RANGE_UPDATE at index={} - {} active workers, state=ACTIVE, myRange={}",
                            nodeId, entry.getIndex(), rangeAssignments.size(), getMyRange(nodeId));
                    getAllRanges().forEach((assignedNodeId, range) ->
                            log.info("[StateMachine:{}]   {} -> {}", nodeId, assignedNodeId, range));
                }
                case HEARTBEAT -> {
                    synchronized (stateLock) {
                        workerHeartbeats.put(cmd.nodeId(), cmd.heartbeatEpochMs());
                    }
                    log.debug("[StateMachine:{}] Applied HEARTBEAT from {} at epochMs={}",
                            nodeId, cmd.nodeId(), cmd.heartbeatEpochMs());
                }
            }

            updateLastAppliedTermIndex(entry.getTerm(), entry.getIndex());
            return CompletableFuture.completedFuture(Message.EMPTY);
        } catch (Exception e) {
            log.error("[StateMachine:{}] Failed to apply transaction at term={}, index={}",
                    nodeId, entry.getTerm(), entry.getIndex(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    public HashRange getMyRange(String nodeId) {
        synchronized (stateLock) {
            return rangeAssignments.get(nodeId);
        }
    }

    public Map<String, HashRange> getAllRanges() {
        synchronized (stateLock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(rangeAssignments));
        }
    }

    public Map<String, Long> getWorkerHeartbeats() {
        synchronized (stateLock) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(workerHeartbeats));
        }
    }

    public Set<String> getLiveWorkers(long nowEpochMs, long timeoutMs, Set<String> allowedNodeIds) {
        synchronized (stateLock) {
            Set<String> live = new LinkedHashSet<>();
            workerHeartbeats.forEach((workerId, heartbeatEpochMs) -> {
                if (allowedNodeIds.contains(workerId) && nowEpochMs - heartbeatEpochMs <= timeoutMs) {
                    live.add(workerId);
                }
            });
            return Collections.unmodifiableSet(live);
        }
    }

    public boolean isActive() {
        return clusterState.get() == ClusterState.ACTIVE;
    }

    public ClusterState getClusterState() {
        return clusterState.get();
    }
}
