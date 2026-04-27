# Polling and Kafka Producer Implementation

## Overview

This implementation provides a complete polling and event publishing system for the Kairos Scheduler Service. The system consists of:

1. **PollingWorker** - Main polling loop that continuously polls for scheduled tasks within allocated hash ranges
2. **RecoveryWorker** - Background process that scans for orphaned/pending tasks and triggers recovery
3. **TaskEventProducer** - Kafka producer that publishes task execution events using CloudEvents format
4. **Cassandra Repository** - Data access layer for querying scheduled tasks

## Architecture

### Polling Worker Flow

```
┌─────────────────────────────────────────────┐
│ PollingWorker (runs every 1 second)        │
├─────────────────────────────────────────────┤
│ 1. Check if rebalancing in progress        │
│ 2. Get node's assigned hash range           │
│ 3. Query tasks for current time window      │
│ 4. Filter out recently processed tasks     │
│ 5. Publish to Kafka                        │
│ 6. Mark tasks as processed                 │
└─────────────────────────────────────────────┘
```

### Recovery Worker Flow

```
┌─────────────────────────────────────────────┐
│ RecoveryWorker (runs every 5 minutes)      │
├─────────────────────────────────────────────┤
│ 1. Scan tasks from last 30 minutes         │
│ 2. Find PENDING tasks past execution time  │
│ 3. Group by expiration status              │
│ 4. Retry recently expired tasks            │
│ 5. Mark old expired tasks as failed        │
└─────────────────────────────────────────────┘
```

## Components

### 1. TaskSchedule Entity
- **Location**: `entity/TaskSchedule.java`
- **PrimaryKey**: (next_execution_time, task_id)
- **Fields**:
  - `nextExecutionTime`: Unix timestamp (seconds)
  - `taskId`: Unique task identifier
  - `jobId`: Associated job identifier
  - `ringHash`: Hash ring value (0-999999)
  - `status`: PENDING, RUNNING, COMPLETED, FAILED
  - `correlationId`: For distributed tracing
  - `payload`: Task data as JSON
  - `attemptCount`, `maxRetries`: For retry logic

### 2. Cassandra Repository
- **Location**: `repository/TaskScheduleRepository.java`
- **Key Methods**:
  - `findTasksByTimeAndHashRange()`: Main polling query
  - `findPendingTasksByTime()`: Recovery query
  - Queries include ALLOW FILTERING for secondary conditions

### 3. Kafka Producer Config
- **Location**: `kafka/config/KafkaProducerConfig.java`
- **Configuration**:
  - Acks: all (ensures durability)
  - Compression: snappy
  - Batch Size: 16KB
  - Linger MS: 10ms
  - Retries: 3

### 4. CloudEvents Format
- **Location**: `kafka/event/CloudEvent.java`
- **Event Fields**:
  - `type`: Event type (e.g., "io.kairos.scheduler.task.execution")
  - `id`: Unique event ID
  - `source`: Event source (scheduler node)
  - `specversion`: "1.0"
  - `correlationId`: For tracing
  - `data`: Event payload

### 5. Task Event Producer
- **Location**: `kafka/producer/TaskEventProducer.java`
- **Methods**:
  - `publishTaskExecution()`: Single task event
  - `publishTaskExecutionBatch()`: Batch processing
  - `publishOrphanedTaskEvent()`: Recovery events

### 6. Polling Worker
- **Location**: `polling/PollingWorker.java`
- **Features**:
  - Configurable polling interval (default: 1s)
  - Time window-based queries (default: 60s)
  - Rebalancing-aware (pauses during cluster changes)
  - Duplicate prevention using in-memory cache
  - Graceful error handling

### 7. Recovery Worker
- **Location**: `polling/RecoveryWorker.java`
- **Features**:
  - Configurable scan interval (default: 5 minutes)
  - Lookback window (default: 30 minutes)
  - Categorizes orphaned tasks by expiration status
  - Handles retry logic based on max_retries

## Configuration

Add to `application.yaml`:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
      batch-size: 16384
      linger-ms: 10
      compression-type: snappy

kairos:
  kafka:
    topic:
      task-execution: task-execution
  scheduler:
    polling-interval-ms: 1000
    time-window-seconds: 60
    recovery-scan-interval-ms: 300000  # 5 minutes
    recovery-lookback-minutes: 30
```

### Environment Variables

```bash
# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
KAFKA_PRODUCER_ACKS=all
KAFKA_TOPIC_TASK_EXECUTION=task-execution

# Scheduler
POLLING_INTERVAL_MS=1000
TIME_WINDOW_SECONDS=60
RECOVERY_SCAN_INTERVAL_MS=300000
RECOVERY_LOOKBACK_MINUTES=30
NODE_ID=scheduler-1
```

## Database Schema

The Cassandra `task_schedule` table uses:
- **Partition Key**: `next_execution_time` (for time-based queries)
- **Clustering Key**: `task_id`
- **Indices**: status, ring_hash, job_id, correlation_id
- **TTL**: 30 days (automatic cleanup)
- **Compaction**: TimeWindowCompactionStrategy (optimized for time-series data)

See `resources/cassandra/schema.cql` for complete schema.

## Query Patterns

### Main Polling Query
```sql
SELECT * FROM task_schedule 
WHERE next_execution_time = ?
AND ring_hash >= ? 
AND ring_hash < ?
ALLOW FILTERING
```

### Recovery Query
```sql
SELECT * FROM task_schedule 
WHERE next_execution_time <= ?
AND status = 'PENDING'
ALLOW FILTERING
```

## Event Format

### Task Execution Event
```json
{
  "type": "io.kairos.scheduler.task.execution",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "source": "scheduler://scheduler-1",
  "specversion": "1.0",
  "correlationid": "correlation-123",
  "datacontenttype": "application/json",
  "time": "1714000860000",
  "data": {
    "taskId": "task-123",
    "jobId": "job-456",
    "status": "PENDING",
    "nextExecutionTime": 1714000860,
    "ringHash": 536870912,
    "payload": "{}",
    "attemptCount": 0,
    "maxRetries": 3,
    "metadata": {}
  }
}
```

### Orphaned Task Event
```json
{
  "type": "io.kairos.scheduler.task.orphaned",
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "source": "scheduler://scheduler-1",
  "specversion": "1.0",
  "correlationid": "correlation-123",
  "data": {
    "taskId": "task-123",
    "jobId": "job-456",
    "status": "PENDING",
    "recovery_reason": "orphaned_job",
    ...
  }
}
```

## Key Features

### 1. Rebalancing Awareness
- Polling pauses automatically when cluster is rebalancing
- Resumes once cluster reaches ACTIVE state

### 2. Duplicate Prevention
- In-memory cache of recently processed task IDs
- Automatic cache eviction at 10K tasks

### 3. Hash Range Filtering
- Tasks are filtered by assigned hash range
- Prevents cross-node task processing
- Supports consistent hashing for load balancing

### 4. Orphaned Task Recovery
- Scans every 5 minutes for stuck tasks
- Distinguishes between recoverable and expired tasks
- Publishes separate recovery events

### 5. Graceful Error Handling
- Non-blocking async publishing
- Comprehensive error logging
- Polling continues even if Kafka publishing fails

## Monitoring

### Metrics to Track

1. **Polling Worker**:
   - Tasks polled per cycle
   - Publishing success/failure rate
   - Poll cycle duration
   - Active/idle status

2. **Recovery Worker**:
   - Orphaned tasks found per scan
   - Successfully recovered tasks
   - Expired tasks (beyond retry limit)
   - Scan duration

3. **Kafka Producer**:
   - Publishing latency
   - Batch sizes
   - Compression ratio
   - Failed publishes

## Error Scenarios

### 1. Cluster Rebalancing
- **Behavior**: Polling pauses
- **Recovery**: Automatic resume on ACTIVE state

### 2. Kafka Unavailable
- **Behavior**: Publishing fails but polling continues
- **Recovery**: Recovery worker will find tasks in next scan

### 3. Task Orphaned
- **Behavior**: Task remains PENDING past execution time
- **Recovery**: Recovery worker triggers retry event

### 4. Max Retries Exceeded
- **Behavior**: Task marked as failed
- **Recovery**: Manual intervention required

## Future Enhancements

1. **Metrics Export**: Add Prometheus metrics
2. **Dead Letter Queue**: For definitively failed tasks
3. **Dynamic Configuration**: Update intervals without restart
4. **Partitioning Strategy**: More granular task distribution
5. **Circuit Breaker**: For Kafka failures
