# Kairos Scheduler - Polling & Kafka Integration Architecture

## System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Kairos Scheduler Service                      │
│                                                                       │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    Spring Boot Application                    │ │
│  │  @EnableScheduling, @SpringBootApplication                   │ │
│  │                                                                │ │
│  │  ┌──────────────────────────────────────────────────────────┐│ │
│  │  │             POLLING SUBSYSTEM                            ││ │
│  │  │                                                           ││ │
│  │  │  ┌─────────────────┐      ┌──────────────────┐          ││ │
│  │  │  │ PollingWorker   │      │ RecoveryWorker   │          ││ │
│  │  │  │ @Scheduled      │      │ @Scheduled       │          ││ │
│  │  │  │ (1000ms)        │      │ (300000ms)       │          ││ │
│  │  │  │                 │      │                  │          ││ │
│  │  │  │ • Poll tasks    │      │ • Scan 30min     │          ││ │
│  │  │  │ • Check hash    │      │ • Find orphaned  │          ││ │
│  │  │  │ • Filter dups   │      │ • Handle retries │          ││ │
│  │  │  │ • Skip rebal    │      │ • Mark failed    │          ││ │
│  │  │  └────────┬────────┘      └────────┬─────────┘          ││ │
│  │  │           │                        │                     ││ │
│  │  └───────────┼────────────────────────┼─────────────────────┘│ │
│  │              │                        │                       │ │
│  │  ┌───────────▼────────────────────────▼──────────────────┐  │ │
│  │  │          TaskEventProducer (Kafka Producer)          │  │ │
│  │  │                                                       │  │ │
│  │  │  • Build CloudEvents                                │  │ │
│  │  │  • Async publishing with futures                   │  │ │
│  │  │  • Batch support                                   │  │ │
│  │  │  • Error handling & logging                        │  │ │
│  │  └───────────┬────────────────────────────────────────┘  │ │
│  │              │                                             │ │
│  └──────────────┼─────────────────────────────────────────────┘ │
│                 │                                               │
│  ┌──────────────▼─────────────────────────────────────────────┐ │
│  │             DATA ACCESS LAYER                             │ │
│  │                                                             │ │
│  │  ┌──────────────────────┐    ┌──────────────────────────┐ │ │
│  │  │ TaskScheduleRepository │    │ RaftClusterManager    │ │ │
│  │  │                        │    │ (Hash Range Mgmt)      │ │ │
│  │  │ Queries:               │    │                        │ │ │
│  │  │ • findByTimeAndRange() │    │ • Get my range         │ │ │
│  │  │ • findPendingByTime()  │    │ • Check rebalancing    │ │ │
│  │  │ • findByTime()         │    │ • Get all ranges       │ │ │
│  │  └──────────────────────┘    └──────────────────────────┘ │ │
│  │                                                             │ │
│  └────────────────┬──────────────────────┬────────────────────┘ │
│                   │                      │                       │
└───────────────────┼──────────────────────┼───────────────────────┘
                    │                      │
     ┌──────────────▼──────────────────────▼─────────────────┐
     │         EXTERNAL SYSTEMS                             │
     │                                                       │
     │  ┌────────────────────┐   ┌──────────────────────┐  │
     │  │    Cassandra       │   │      Kafka           │  │
     │  │                    │   │                      │  │
     │  │ task_schedule      │   │ task-execution       │  │
     │  │ • Partition: time  │   │ • CloudEvents msgs   │  │
     │  │ • Cluster: task_id │   │ • Partitions: 3      │  │
     │  │ • Indices: hash    │   │ • Replication: 3     │  │
     │  │ • TTL: 30 days     │   │                      │  │
     │  └────────────────────┘   └──────────────────────┘  │
     │                                                       │
     └───────────────────────────────────────────────────────┘
```

## Data Flow Diagrams

### Main Polling Flow

```
┌─────────────────────────────────────────────────────────────────┐
│ PollingWorker.pollAndExecuteTasks() [every 1000ms]             │
└──────────────────────┬──────────────────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Check if already polling     │
        │ (skip if polling)            │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Is cluster rebalancing?      │
        │ (check ClusterState)         │
        └──────┬───────────────────────┘
               │ YES
               ├─────────────────► PAUSE & RETURN
               │ NO
               ▼
        ┌──────────────────────────────┐
        │ Get my hash range            │
        │ stateMachine.getMyRange()    │
        └──────────────────────────────┘
               │
               ▼
        ┌──────────────────────────────┐
        │ Calculate time window        │
        │ current_time + 60s           │
        └──────────────────────────────┘
               │
               ▼
        ┌──────────────────────────────┐
        │ Query Cassandra:             │
        │ SELECT * FROM task_schedule  │
        │ WHERE:                       │
        │  - next_execution_time = ?   │
        │  - ring_hash >= start        │
        │  - ring_hash < end           │
        └──────────────────────────────┘
               │
               ▼
        ┌──────────────────────────────┐
        │ Filter recently processed    │
        │ (avoid duplicates)           │
        └──────────────────────────────┘
               │
               ▼
        ┌──────────────────────────────┐
        │ Publish to Kafka (batch)     │
        │ TaskEventProducer.publish()  │
        └──────────────────────────────┘
               │
               ▼
        ┌──────────────────────────────┐
        │ Mark as processed            │
        │ (in-memory cache)            │
        └──────────────────────────────┘
```

### Recovery Worker Flow

```
┌──────────────────────────────────────────────────────────────┐
│ RecoveryWorker.scanAndRecoverOrphanedTasks() [every 5min]   │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Check if already recovering  │
        │ (skip if in progress)        │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Calculate lookback window    │
        │ current_time - 30min         │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Query Cassandra:             │
        │ SELECT * FROM task_schedule  │
        │ WHERE:                       │
        │  - next_execution_time <= ?  │
        │  - status = 'PENDING'        │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Group tasks by age           │
        │ - Recently expired           │
        │ - Beyond recovery window     │
        └──────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        │ (split)                      │
        │                              │
        ▼                              ▼
  ┌──────────────┐           ┌─────────────────┐
  │ Recently     │           │ Beyond window   │
  │ expired      │           │                 │
  │              │           │ Mark as failed  │
  │ Attempt      │           │ Log expiration  │
  │ retry        │           │                 │
  └──────┬───────┘           └─────────────────┘
         │
         ▼
  ┌──────────────────────────┐
  │ Check attempt_count <    │
  │ max_retries              │
  └──────┬────────────────┬──┘
         │ YES            │ NO
         │                └─► Mark as FAILED
         │
         ▼
  ┌──────────────────────────┐
  │ Publish orphaned event   │
  │ TaskEventProducer        │
  │ .publishOrphanedTask()   │
  └──────────────────────────┘
```

### CloudEvent Publishing Flow

```
┌──────────────────────────────────────────────────────────────┐
│ TaskEventProducer.publishTaskExecution(task)                │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Build event data map         │
        │ - taskId, jobId              │
        │ - ringHash, status           │
        │ - payload, metadata          │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Create CloudEvent            │
        │ - type: task.execution       │
        │ - id: UUID                   │
        │ - source: scheduler://node   │
        │ - correlationId: from task   │
        │ - specversion: 1.0           │
        │ - data: event data           │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Serialize to JSON            │
        │ ObjectMapper.writeValueAsStr │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Build Message with Headers   │
        │ - payload: event JSON        │
        │ - key: jobId (partition)     │
        │ - headers: ce_type, ce_id... │
        │ - topic: task-execution      │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ Send to Kafka (async)        │
        │ kafkaTemplate.send()         │
        └──────────────────────────────┘
                       │
                       ▼
        ┌──────────────────────────────┐
        │ CompletableFuture            │
        │ - onSuccess: log & continue  │
        │ - onError: log error         │
        └──────────────────────────────┘
```

## Query Patterns

### Pattern 1: Main Polling Query
```
PARTITION BY: next_execution_time (time-based sharding)
CLUSTER BY: task_id
FILTER: ring_hash in [start, end)

Example:
SELECT * FROM task_schedule 
WHERE next_execution_time = 1714000860
  AND ring_hash >= 0 
  AND ring_hash < 1073741824
ALLOW FILTERING

Time Complexity: O(log N) partition search
Space Complexity: O(K) where K = tasks in time slot
```

### Pattern 2: Recovery Query
```
PARTITION BY: next_execution_time
CLUSTER BY: task_id
FILTER: status = 'PENDING' AND time <= target

Example:
SELECT * FROM task_schedule 
WHERE next_execution_time <= 1714000920
  AND status = 'PENDING'
ALLOW FILTERING

Scans: All partitions <= target time
Filters: status = 'PENDING'
```

## Event Schema (CloudEvents)

```json
{
  "specversion": "1.0",
  "type": "io.kairos.scheduler.task.execution",
  "source": "scheduler://scheduler-1",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "time": "2024-04-25T10:01:00Z",
  "datacontenttype": "application/json",
  "correlationid": "correlation-123",
  "data": {
    "taskId": "task-123",
    "jobId": "job-456",
    "status": "PENDING",
    "nextExecutionTime": 1714000860,
    "ringHash": 536870912,
    "payload": "{...}",
    "attemptCount": 0,
    "maxRetries": 3,
    "metadata": {
      "environment": "production",
      "priority": "high"
    }
  }
}
```

## Cassandra Partitioning Strategy

```
Partition Key: next_execution_time
├── Advantages:
│   ├── Time-based queries are efficient
│   ├── Natural alignment with TTL
│   ├── Supports time-window compaction
│   └── Query recent data quickly
│
└── Challenges:
    ├── Hot partitions during peak times
    ├── Need to query multiple partitions
    └── Requires careful time window selection

Clustering Key: task_id
├── Advantages:
│   ├── Ensures uniqueness per time slot
│   ├── Ordered sorting within partition
│   └── Efficient deduplication
│
└── Clustering Order: ASC (for range queries)

Secondary Indices:
├── status: For pending task queries
├── ring_hash: For range filtering
├── job_id: For job tracking
└── correlation_id: For distributed tracing
```

## State Machine - Rebalancing Impact

```
                    ┌─────────────────┐
                    │    ACTIVE       │
                    │ Polling: ON     │
                    │ Recovery: ON    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  REASSIGNING    │
                    │ Polling: OFF    │
                    │ Recovery: ON    │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │  INITIALIZING   │
                    │ Polling: OFF    │
                    │ Recovery: OFF   │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │    ACTIVE       │
                    │ Polling: ON     │
                    │ Recovery: ON    │
                    └─────────────────┘
```

## Performance Metrics

### Throughput
- Polling frequency: 1 task/ms at 1000 tasks/second
- Batch publishing: 16KB batches
- Kafka write throughput: ~100MB/s per broker

### Latency
- Polling cycle: ~100-500ms (including I/O)
- Cassandra query: ~10-50ms
- Kafka publish: ~5-20ms
- End-to-end: <1 second

### Storage
- Cassandra: ~1KB per task record
- Retention: 30 days (TTL)
- Growth: ~1MB per 1M tasks

## Failure Scenarios & Recovery

```
Scenario: Cluster Rebalancing
├─ Detection: ClusterState = REASSIGNING
├─ Action: Polling pauses
├─ Duration: Seconds to minutes
└─ Recovery: Automatic on state transition

Scenario: Kafka Unavailable
├─ Detection: Publishing fails
├─ Action: Error logged, polling continues
├─ Duration: Until Kafka recovers
└─ Recovery: RecoveryWorker finds stuck tasks

Scenario: Task Orphaned
├─ Detection: PENDING past execution time
├─ Action: RecoveryWorker triggers retry
├─ Duration: Exponential backoff
└─ Recovery: Event published for retry

Scenario: Database Partition
├─ Detection: Cassandra query fails
├─ Action: Poll cycle skipped
├─ Duration: Until partition heals
└─ Recovery: RecoveryWorker compensates
```
