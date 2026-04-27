# Quick Start Guide - Polling & Kafka Integration

## Prerequisites

1. **Cassandra**: Running on localhost:9042
2. **Kafka**: Running on localhost:9092
3. **Java 21+**
4. **Maven 3.8+**

## Setup Steps

### 1. Initialize Cassandra Schema

```bash
# Connect to Cassandra
cqlsh localhost 9042

# Run schema creation
SOURCE './src/main/resources/cassandra/schema.cql';
```

Or run via Docker:
```bash
docker exec -i cassandra cqlsh < src/main/resources/cassandra/schema.cql
```

### 2. Create Kafka Topic

```bash
kafka-topics.sh --create \
  --topic task-execution \
  --partitions 3 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

### 3. Build and Run

```bash
# Build
mvn clean package

# Run locally
mvn spring-boot:run

# Or run jar
java -jar target/scheduler-service-0.0.1-SNAPSHOT.jar
```

### 4. Configuration via Environment Variables

```bash
# Cassandra
export CASSANDRA_CONTACT_POINTS=localhost
export CASSANDRA_PORT=9042

# Kafka
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export KAFKA_TOPIC_TASK_EXECUTION=task-execution

# Scheduler
export NODE_ID=scheduler-1
export RAFT_PORT=9090
export RAFT_ADDRESS=localhost
export RAFT_PEERS=scheduler-1:localhost:9090

# Polling
export POLLING_INTERVAL_MS=1000
export TIME_WINDOW_SECONDS=60

# Recovery
export RECOVERY_SCAN_INTERVAL_MS=300000
export RECOVERY_LOOKBACK_MINUTES=30
```

## API Endpoints

### Health Check
```bash
GET /api/v1/workers/health
```

Response:
```json
{
  "status": "UP",
  "pollingWorkerActive": true,
  "recoveryWorkerActive": true,
  "systemHealthy": true,
  "timestamp": 1714000860000
}
```

### Worker Status
```bash
GET /api/v1/workers/status
```

### Polling Worker Status
```bash
GET /api/v1/workers/polling/status
```

### Recovery Worker Status
```bash
GET /api/v1/workers/recovery/status
```

## Docker Compose Setup

Save as `docker-compose.yml`:

```yaml
version: '3.8'

services:
  cassandra:
    image: cassandra:5.0
    ports:
      - "9042:9042"
    environment:
      CASSANDRA_CLUSTER_NAME: scheduler-cluster
    healthcheck:
      test: ["CMD", "cqlsh", "-e", "describe cluster"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  scheduler-1:
    build: .
    depends_on:
      cassandra:
        condition: service_healthy
      kafka:
        condition: service_started
    ports:
      - "8081:8081"
      - "9090:9090"
    environment:
      NODE_ID: scheduler-1
      RAFT_PORT: 9090
      RAFT_ADDRESS: scheduler-1
      CASSANDRA_CONTACT_POINTS: cassandra
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
```

Run:
```bash
docker-compose up
```

## Monitoring

### Check Cassandra Table
```bash
cqlsh> SELECT count(*) FROM job_scheduler.task_schedule;
cqlsh> SELECT * FROM job_scheduler.task_schedule LIMIT 10;
```

### Monitor Kafka Topic
```bash
kafka-console-consumer.sh \
  --topic task-execution \
  --bootstrap-server localhost:9092 \
  --from-beginning \
  --max-messages 10
```

### View Application Logs
```bash
# Polling worker logs
tail -f logs/scheduler.log | grep "PollingWorker"

# Recovery worker logs
tail -f logs/scheduler.log | grep "RecoveryWorker"

# Kafka producer logs
tail -f logs/scheduler.log | grep "TaskEventProducer"
```

## Testing

### Insert Test Task
```bash
# Connect to Cassandra
cqlsh localhost 9042

# Insert test task
INSERT INTO job_scheduler.task_schedule (
    next_execution_time, 
    task_id, 
    job_id, 
    ring_hash, 
    status, 
    payload, 
    created_at, 
    updated_at,
    attempt_count,
    max_retries
) VALUES (
    1714000860,
    'test-task-1',
    'job-123',
    536870912,
    'PENDING',
    '{"action": "send_email"}',
    toTimestamp(now()),
    toTimestamp(now()),
    0,
    3
);
```

### Verify Event Publishing
```bash
# Wait 1-2 seconds for polling cycle
# Check Kafka topic for event
kafka-console-consumer.sh \
  --topic task-execution \
  --bootstrap-server localhost:9092 \
  --from-beginning \
  --max-messages 1 \
  --property print.key=true \
  --property print.value=true
```

## Performance Tuning

### For High Throughput
```yaml
# Increase batch size and reduce linger
spring.kafka.producer.batch-size: 32768
spring.kafka.producer.linger-ms: 5

# Increase polling frequency
kairos.scheduler.polling-interval-ms: 500

# Reduce time window for faster query response
kairos.scheduler.time-window-seconds: 30
```

### For Low Latency
```yaml
# Smaller batches for immediate publishing
spring.kafka.producer.batch-size: 8192
spring.kafka.producer.linger-ms: 0

# More frequent polling
kairos.scheduler.polling-interval-ms: 100
```

## Troubleshooting

### No Events Being Published
1. Check polling worker is active: `GET /api/v1/workers/polling/status`
2. Verify tasks exist in Cassandra: `SELECT count(*) FROM task_schedule`
3. Check Kafka connectivity: `GET /api/v1/workers/health`
4. Verify hash range assignment: Check RAFT logs

### High CPU Usage
1. Reduce `POLLING_INTERVAL_MS` to 1000ms or higher
2. Increase `TIME_WINDOW_SECONDS` to reduce query frequency
3. Add Cassandra caching layer

### Memory Issues
1. Clear recently-processed cache more frequently
2. Reduce recovery lookback window
3. Implement pagination for large result sets

## Architecture Summary

```
┌─────────────────────────────────────────────────────┐
│                Spring Boot App                       │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────────┐      ┌──────────────────┐   │
│  │ PollingWorker    │      │ RecoveryWorker   │   │
│  │ (every 1s)       │      │ (every 5min)     │   │
│  └────────┬─────────┘      └─────────┬────────┘   │
│           │                          │            │
│           └──────────────┬───────────┘            │
│                          │                        │
│                  ┌───────▼────────┐              │
│                  │ TaskEventProducer              │
│                  │ (CloudEvents)  │              │
│                  └───────┬────────┘              │
└──────────────────────────┼─────────────────────────┘
                           │
                  ┌────────▼────────┐
                  │    Kafka        │
                  │ (task-execution)│
                  └─────────────────┘
                           ▲
                           │
                  ┌────────┴────────┐
                  │   Cassandra     │
                  │  (task_schedule)│
                  └─────────────────┘
```

## Next Steps

1. Deploy to production cluster
2. Set up monitoring (Prometheus metrics)
3. Configure alerting for failed tasks
4. Implement dead letter queue for errors
5. Add circuit breaker for Kafka failures
