package io.kairos.scheduler.raft;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcConfigKeys;
import org.apache.ratis.protocol.*;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.RaftServerConfigKeys;
import org.apache.ratis.util.TimeDuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@Slf4j
@Component
public class RaftNode {

    @Getter
    @Value("${kairos.raft.node-id}")
    private String nodeId;

    @Value("${kairos.raft.port}")
    private int port;

    @Value("${kairos.raft.address:localhost}")
    private String address;

    @Value("${kairos.raft.storage-dir}")
    private String storageDir;

    @Value("${kairos.raft.group-id}")
    private String groupId;

    @Value("${kairos.raft.peers:}")
    private String peersRaw;

    @Value("${kairos.raft.single-node:false}")
    private boolean singleNode;

    @Getter
    private RaftServer server;

    @Getter
    private RaftGroup raftGroup;

    @Getter
    private RaftPeerId localPeerId;

    private final SchedulerStateMachine stateMachine;

    public RaftNode(SchedulerStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @PostConstruct
    public void start() throws IOException {
        localPeerId = RaftPeerId.valueOf(nodeId);
        RaftGroupId gid = RaftGroupId.valueOf(UUID.fromString(groupId));
        List<RaftPeer> peers = parsePeers();

        boolean selfPresent = peers.stream()
                .anyMatch(p -> p.getId().equals(localPeerId));
        if (!selfPresent) {
            throw new IllegalStateException("Raft peer list must include this node. nodeId="
                    + nodeId + ", peers=" + peersRaw);
        }

        raftGroup = RaftGroup.valueOf(gid, peers);

        RaftProperties props = buildProperties();

        server = RaftServer.newBuilder()
                .setServerId(localPeerId)
                .setGroup(raftGroup)
                .setStateMachine(stateMachine)
                .setProperties(props)
                .build();

        server.start();
        log.info("[Raft] Node {} started on {}:{} with {} peers: {}",
                nodeId, address, port, peers.size(), describePeers(peers));
    }

    @Scheduled(fixedDelay = 10_000)
    public void logRaftState() {
        try {
            var division = server.getDivision(raftGroup.getGroupId());
            var info = division.getInfo();
            log.info("[Raft] Node={} Leader={} Role={} ClusterState={} Range={}",
                    nodeId,
                    getLeaderId().orElse("none"),
                    info.getCurrentRole(),
                    stateMachine.getClusterState(),
                    stateMachine.getMyRange(nodeId));
        } catch (Exception ignored) {
        }
    }

    @PreDestroy
    public void stop() throws IOException {
        if (server != null) {
            server.close();
            log.info("[Raft] Node {} stopped", nodeId);
        }
    }

    public boolean isLeader() {
        try {
            return server.getDivision(raftGroup.getGroupId()).getInfo().isLeader();
        } catch (Exception e) {
            return false;
        }
    }

    public Optional<String> getLeaderId() {
        try {
            RaftPeerId id = server.getDivision(raftGroup.getGroupId()).getInfo().getLeaderId();
            return Optional.ofNullable(id).map(RaftPeerId::toString);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private RaftProperties buildProperties() {
        RaftProperties props = new RaftProperties();

        GrpcConfigKeys.Server.setPort(props, port);

        File dir = new File(storageDir, nodeId);
        dir.mkdirs();
        RaftServerConfigKeys.setStorageDir(props, List.of(dir));

        RaftServerConfigKeys.Rpc.setTimeoutMin(props,
                TimeDuration.valueOf(2, TimeUnit.SECONDS));
        RaftServerConfigKeys.Rpc.setTimeoutMax(props,
                TimeDuration.valueOf(5, TimeUnit.SECONDS));

        RaftServerConfigKeys.Rpc.setFirstElectionTimeoutMin(props,
                TimeDuration.valueOf(3, TimeUnit.SECONDS));
        RaftServerConfigKeys.Rpc.setFirstElectionTimeoutMax(props,
                TimeDuration.valueOf(7, TimeUnit.SECONDS));

        return props;
    }

    private List<RaftPeer> parsePeers() {
        if (peersRaw == null || peersRaw.isBlank()) {
            if (!singleNode) {
                throw new IllegalStateException("kairos.raft.peers/RAFT_PEERS is blank. "
                        + "For a cluster, pass the same full peer list to every node. "
                        + "For local single-node mode, set RAFT_SINGLE_NODE=true.");
            }
            log.warn("[Raft] Starting in explicit single-node mode");
            return new ArrayList<>(Collections.singletonList(
                    RaftPeer.newBuilder()
                            .setId(localPeerId)
                            .setAddress(address + ":" + port)
                            .build()));
        }

        List<RaftPeer> peers = new ArrayList<>();
        Set<String> peerIds = new HashSet<>();
        for (String rawEntry : peersRaw.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.split(":");
            if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException("Invalid Raft peer entry '" + entry
                        + "'. Expected format: nodeId:host:port");
            }
            if (!peerIds.add(parts[0])) {
                throw new IllegalArgumentException("Duplicate Raft peer id in peer list: " + parts[0]);
            }

            int peerPort;
            try {
                peerPort = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid Raft peer port in entry '" + entry + "'", e);
            }

            peers.add(RaftPeer.newBuilder()
                    .setId(parts[0])
                    .setAddress(parts[1] + ":" + peerPort)
                    .build());
        }

        if (peers.isEmpty()) {
            throw new IllegalStateException("No valid Raft peers parsed from: " + peersRaw);
        }
        return peers;
    }

    private String describePeers(List<RaftPeer> peers) {
        return peers.stream()
                .map(peer -> peer.getId() + "@" + peer.getAddress())
                .toList()
                .toString();
    }
}
