-- Cassandra Schema for Kairos Scheduler Service
-- Task Schedule Table

-- Create keyspace
CREATE KEYSPACE IF NOT EXISTS job_scheduler
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 3};

-- Create task_schedule table
CREATE TABLE IF NOT EXISTS job_scheduler.task_schedule (
    next_execution_time bigint,              -- Partition key: execution time in seconds
    task_id text,                           -- Clustering key: unique task identifier
    job_id text,                            -- Job identifier
    correlation_id text,                    -- Correlation ID for tracing
    ring_hash int,                          -- Hash ring value for distribution
    status text,                            -- Status: PENDING, RUNNING, COMPLETED, FAILED
    payload text,                           -- Task payload (JSON)
    source text,                            -- Source of the task
    created_at timestamp,                   -- Creation timestamp
    updated_at timestamp,                   -- Last update timestamp
    attempt_count int,                      -- Number of execution attempts
    max_retries int,                        -- Maximum number of retries
    metadata map<text, text>,               -- Additional metadata
    
    PRIMARY KEY (next_execution_time, task_id)
) WITH CLUSTERING ORDER BY (task_id ASC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy', 'compaction_window_unit': 'DAYS', 'compaction_window_size': 1}
  AND default_time_to_live = 2592000;  -- 30-day TTL


-- Create indices for efficient querying
CREATE INDEX IF NOT EXISTS idx_task_schedule_status ON job_scheduler.task_schedule (status);
CREATE INDEX IF NOT EXISTS idx_task_schedule_ring_hash ON job_scheduler.task_schedule (ring_hash);
CREATE INDEX IF NOT EXISTS idx_task_schedule_job_id ON job_scheduler.task_schedule (job_id);
CREATE INDEX IF NOT EXISTS idx_task_schedule_correlation_id ON job_scheduler.task_schedule (correlation_id);
