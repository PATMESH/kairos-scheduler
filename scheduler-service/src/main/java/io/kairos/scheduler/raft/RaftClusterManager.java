package io.kairos.scheduler.raft;

import io.kairos.scheduler.hashing.HashRange;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.conf.Parameters;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.proto.RaftProtos.ReplicationLevel;
import org.apache.ratis.protocol.*;
import org.apache.ratis.retry.RetryPolicies;
import org.apache.ratis.util.TimeDuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class RaftClusterManager {

    private final RaftNode raftNode;
    private final SchedulerStateMachine stateMachine;

    private volatile boolean rangesInitialized = false;
    private final AtomicBoolean rebalanceInProgress = new AtomicBoolean(false);

    @Value("${kairos.scheduler.worker-heartbeat-timeout-ms:8000}")
    private long workerHeartbeatTimeoutMs;

    public RaftClusterManager(RaftNode raftNode, SchedulerStateMachine stateMachine) {
        this.raftNode = raftNode;
        this.stateMachine = stateMachine;
    }

    @Scheduled(fixedDelay = 3000)
    public void checkAndInitializeRanges() {
        if (rangesInitialized || !raftNode.isLeader()) {
            return;
        }

        try {
            if (stateMachine.getClusterState() == ClusterState.ACTIVE && !stateMachine.getAllRanges().isEmpty()) {
                rangesInitialized = true;
                return;
            }

            log.info("[ClusterManager] This node is leader, initializing ranges...");
            Collection<RaftPeer> peers = getCurrentPeers();
            Map<String, HashRange> ranges = calculateRanges(peers);
            submitRangeUpdate(ranges, ReplicationLevel.ALL_COMMITTED);
            rangesInitialized = true;
            log.info("[ClusterManager] Initial ranges published for {} nodes", peers.size());
        } catch (Exception e) {
            log.warn("[ClusterManager] Failed to initialize ranges, will retry", e);
        }
    }

    @Scheduled(fixedDelayString = "${kairos.scheduler.worker-heartbeat-interval-ms:2000}")
    public void publishWorkerHeartbeat() {
        if (raftNode.getLeaderId().isEmpty() && !raftNode.isLeader()) {
            return;
        }

        try {
            submitHeartbeat(raftNode.getNodeId(), System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("[ClusterManager] Failed to publish heartbeat for {}", raftNode.getNodeId(), e);
        }
    }

    @Scheduled(fixedDelayString = "${kairos.scheduler.worker-liveness-check-ms:10000}")
    public void rebalanceLiveWorkers() {
        if (!rangesInitialized || !raftNode.isLeader()) {
            return;
        }
        if (!rebalanceInProgress.compareAndSet(false, true)) {
            return;
        }

        try {
            Set<String> raftPeerIds = getCurrentPeerIds();
            Set<String> liveWorkers = stateMachine.getLiveWorkers(
                    System.currentTimeMillis(), workerHeartbeatTimeoutMs, raftPeerIds);
            if (liveWorkers.isEmpty()) {
                liveWorkers = new LinkedHashSet<>(Collections.singleton(raftNode.getNodeId()));
            }

            Set<String> currentOwners = new LinkedHashSet<>(stateMachine.getAllRanges().keySet());
            if (currentOwners.equals(liveWorkers)) {
                return;
            }

            log.info("[ClusterManager] Worker liveness changed. currentOwners={}, liveWorkers={}, raftPeers={}",
                    currentOwners, liveWorkers, raftPeerIds);

            submitReassigning();
            Map<String, HashRange> newRanges = calculateRangesForNodeIds(liveWorkers);
            submitRangeUpdate(newRanges, ReplicationLevel.MAJORITY_COMMITTED);
            log.info("[ClusterManager] Worker ranges rebalanced over live workers {}", liveWorkers);
        } catch (Exception e) {
            log.warn("[ClusterManager] Failed to rebalance live workers", e);
        } finally {
            rebalanceInProgress.set(false);
        }
    }

    public void addNode(String peerId, String peerAddress) throws Exception {
        ensureLeader();
        log.info("[ClusterManager] Adding node {} at {}", peerId, peerAddress);

        submitReassigning();

        List<RaftPeer> currentPeers = new ArrayList<>(getCurrentPeers());
        RaftPeer newPeer = RaftPeer.newBuilder()
                .setId(peerId)
                .setAddress(peerAddress)
                .build();

        boolean alreadyExists = currentPeers.stream()
                .anyMatch(p -> p.getId().toString().equals(peerId));
        if (alreadyExists) {
            log.warn("[ClusterManager] Node {} already in cluster", peerId);
        } else {
            currentPeers.add(newPeer);
        }

        try (RaftClient client = buildClient(raftNode.getLocalPeerId())) {
            RaftClientReply reply = client.admin().setConfiguration(currentPeers);
            if (!reply.isSuccess()) {
                throw new RuntimeException("setConfiguration failed: " + reply.getException());
            }
            log.info("[ClusterManager] Raft configuration updated, {} peers", currentPeers.size());
        }

        Map<String, HashRange> newRanges = calculateRanges(currentPeers);
        submitRangeUpdate(newRanges, ReplicationLevel.ALL_COMMITTED);
        log.info("[ClusterManager] Node {} added, ranges recalculated", peerId);
    }

    public void removeNode(String peerId) throws Exception {
        ensureLeader();
        log.info("[ClusterManager] Removing node {}", peerId);

        submitReassigning();

        List<RaftPeer> currentPeers = new ArrayList<>(getCurrentPeers());
        currentPeers.removeIf(p -> p.getId().toString().equals(peerId));

        if (currentPeers.isEmpty()) {
            throw new IllegalStateException("Cannot remove the last node from the cluster");
        }

        try (RaftClient client = buildClient(raftNode.getLocalPeerId())) {
            RaftClientReply reply = client.admin().setConfiguration(currentPeers);
            if (!reply.isSuccess()) {
                throw new RuntimeException("setConfiguration failed: " + reply.getException());
            }
            log.info("[ClusterManager] Raft configuration updated, {} peers remaining", currentPeers.size());
        }

        Map<String, HashRange> newRanges = calculateRanges(currentPeers);
        submitRangeUpdate(newRanges, ReplicationLevel.ALL_COMMITTED);
        log.info("[ClusterManager] Node {} removed, ranges recalculated", peerId);
    }

    public Map<String, HashRange> calculateRanges(Collection<RaftPeer> peers) {
        return calculateRangesForNodeIds(peers.stream()
                .map(p -> p.getId().toString())
                .toList());
    }

    public Map<String, HashRange> calculateRangesForNodeIds(Collection<String> nodeIds) {
        List<String> sortedIds = nodeIds.stream()
                .sorted()
                .toList();

        if (sortedIds.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate ranges without Raft peers");
        }

        long total = HashRange.RANGE_MAX + 1; // 1,000,000
        long n = sortedIds.size();
        long perNode = total / n;
        long remainder = total % n;

        Map<String, HashRange> ranges = new LinkedHashMap<>();
        long cursor = 0;
        for (int i = 0; i < n; i++) {
            long size = perNode + (i < remainder ? 1 : 0);
            ranges.put(sortedIds.get(i), new HashRange(cursor, cursor + size - 1));
            cursor += size;
        }
        return ranges;
    }

    public Collection<RaftPeer> getCurrentPeers() throws IOException {
        return raftNode.getServer()
                .getDivision(raftNode.getRaftGroup().getGroupId())
                .getGroup()
                .getPeers();
    }

    public Set<String> getCurrentPeerIds() throws IOException {
        Set<String> peerIds = new LinkedHashSet<>();
        for (RaftPeer peer : getCurrentPeers()) {
            peerIds.add(peer.getId().toString());
        }
        return peerIds;
    }

    private void submitReassigning() throws Exception {
        submitCommand(StateMachineCommand.reassigning(), ReplicationLevel.MAJORITY_COMMITTED);
    }

    private void submitRangeUpdate(Map<String, HashRange> ranges, ReplicationLevel replicationLevel) throws Exception {
        submitCommand(StateMachineCommand.rangeUpdate(ranges), replicationLevel);
    }

    private void submitCommand(StateMachineCommand cmd, ReplicationLevel replicationLevel) throws Exception {
        ensureLeader();
        byte[] payload = cmd.toBytes();
        Message message = Message.valueOf(
                org.apache.ratis.thirdparty.com.google.protobuf.ByteString.copyFrom(payload),
                () -> cmd.type().name());

        try (RaftClient client = buildClient(raftNode.getLocalPeerId())) {
            log.info("[ClusterManager] Submitting {} through Raft log, payloadBytes={}, group={}, waitFor={}",
                    cmd.type(), payload.length, raftNode.getRaftGroup().getGroupId(), replicationLevel);

            RaftClientReply reply = client.io().send(message);
            if (!reply.isSuccess()) {
                throw new RuntimeException("Failed to submit " + cmd.type() + ": " + describeFailure(reply));
            }

            log.info("[ClusterManager] {} committed by leader at logIndex={}, commitInfos={}",
                    cmd.type(), reply.getLogIndex(), reply.getCommitInfos());

            RaftClientReply watchReply = client.io().watch(reply.getLogIndex(), replicationLevel);
            if (!watchReply.isSuccess()) {
                throw new RuntimeException("Failed waiting for " + cmd.type()
                        + " to reach " + replicationLevel + " at logIndex=" + reply.getLogIndex()
                        + ": " + describeFailure(watchReply));
            }

            log.info("[ClusterManager] {} reached {} at logIndex={}, commitInfos={}",
                    cmd.type(), replicationLevel, reply.getLogIndex(), watchReply.getCommitInfos());
        }
    }

    private void submitHeartbeat(String workerNodeId, long heartbeatEpochMs) throws Exception {
        StateMachineCommand cmd = StateMachineCommand.heartbeat(workerNodeId, heartbeatEpochMs);
        byte[] payload = cmd.toBytes();
        Message message = Message.valueOf(
                org.apache.ratis.thirdparty.com.google.protobuf.ByteString.copyFrom(payload),
                () -> cmd.type().name());

        RaftPeerId knownLeader = raftNode.getLeaderId()
                .map(RaftPeerId::valueOf)
                .orElse(raftNode.isLeader() ? raftNode.getLocalPeerId() : null);
        if (knownLeader == null) {
            return;
        }

        try (RaftClient client = buildClient(knownLeader)) {
            RaftClientReply reply = client.io().send(message);
            if (!reply.isSuccess()) {
                throw new RuntimeException("Failed to submit HEARTBEAT: " + describeFailure(reply));
            }
        }
    }

    private RaftClient buildClient(RaftPeerId leaderId) {
        ClientId clientId = ClientId.randomId();
        RaftProperties props = new RaftProperties();
        GrpcFactory grpcFactory = new GrpcFactory(new Parameters());
        RaftClient.Builder builder = RaftClient.newBuilder()
                .setClientId(clientId)
                .setRaftGroup(raftNode.getRaftGroup())
                .setClientRpc(grpcFactory.newRaftClientRpc(clientId, props))
                .setProperties(props)
                .setRetryPolicy(RetryPolicies.retryUpToMaximumCountWithFixedSleep(
                        5, TimeDuration.valueOf(300, TimeUnit.MILLISECONDS)));
        if (leaderId != null) {
            builder.setLeaderId(leaderId);
        }
        return builder.build();
    }

    private void ensureLeader() {
        if (!raftNode.isLeader()) {
            throw new IllegalStateException("Only the Raft leader may submit state-machine writes. Current leader: "
                    + raftNode.getLeaderId().orElse("unknown"));
        }
    }

    private String describeFailure(RaftClientReply reply) {
        if (reply.getNotLeaderException() != null && reply.getNotLeaderException().getSuggestedLeader() != null) {
            return reply.getNotLeaderException() + ", suggestedLeader="
                    + reply.getNotLeaderException().getSuggestedLeader().getId();
        }
        return String.valueOf(reply.getException());
    }
}
