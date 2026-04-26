package io.kairos.scheduler.controller;

import io.kairos.scheduler.raft.RaftClusterManager;
import io.kairos.scheduler.raft.RaftNode;
import io.kairos.scheduler.raft.SchedulerStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/raft")
public class RaftMembershipController {

    private final RaftNode raftNode;
    private final RaftClusterManager clusterManager;
    private final SchedulerStateMachine stateMachine;

    public RaftMembershipController(RaftNode raftNode,
                                    RaftClusterManager clusterManager,
                                    SchedulerStateMachine stateMachine) {
        this.raftNode = raftNode;
        this.clusterManager = clusterManager;
        this.stateMachine = stateMachine;
    }

    @PostMapping("/peers")
    public ResponseEntity<Map<String, Object>> addPeer(@RequestBody PeerRequest request) {
        try {
            if (!raftNode.isLeader()) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "Not the leader",
                        "leader", raftNode.getLeaderId().orElse("unknown")));
            }
            clusterManager.addNode(request.peerId(), request.address());
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Node " + request.peerId() + " added",
                    "ranges", stateMachine.getAllRanges()));
        } catch (Exception e) {
            log.error("[REST] Failed to add peer {}", request.peerId(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/peers/{peerId}")
    public ResponseEntity<Map<String, Object>> removePeer(@PathVariable String peerId) {
        try {
            if (!raftNode.isLeader()) {
                return ResponseEntity.status(409).body(Map.of(
                        "error", "Not the leader",
                        "leader", raftNode.getLeaderId().orElse("unknown")));
            }
            clusterManager.removeNode(peerId);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Node " + peerId + " removed",
                    "ranges", stateMachine.getAllRanges()));
        } catch (Exception e) {
            log.error("[REST] Failed to remove peer {}", peerId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("nodeId", raftNode.getNodeId());
            status.put("isLeader", raftNode.isLeader());
            status.put("leader", raftNode.getLeaderId().orElse("none"));
            status.put("clusterState", stateMachine.getClusterState().name());

            var peers = clusterManager.getCurrentPeers();
            status.put("peers", peers.stream()
                    .map(p -> Map.of("id", p.getId().toString(), "address", p.getAddress()))
                    .toList());
            status.put("workerHeartbeats", stateMachine.getWorkerHeartbeats());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }


    @GetMapping("/ranges")
    public ResponseEntity<Map<String, Object>> getRanges() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clusterState", stateMachine.getClusterState().name());
        result.put("myNodeId", raftNode.getNodeId());
        result.put("myRange", stateMachine.getMyRange(raftNode.getNodeId()));
        result.put("allRanges", stateMachine.getAllRanges());
        result.put("workerHeartbeats", stateMachine.getWorkerHeartbeats());
        return ResponseEntity.ok(result);
    }

    public record PeerRequest(String peerId, String address) {
    }
}
