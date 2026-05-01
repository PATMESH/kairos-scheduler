CREATE KEYSPACE IF NOT EXISTS kairos
    WITH replication = {
        'class': 'SimpleStrategy',
        'replication_factor': 1
    };

USE kairos;

CREATE TABLE IF NOT EXISTS jobs (
    user_id             UUID,
    job_id              UUID,
    callback_url        TEXT,
    payload             TEXT,
    is_recurring        BOOLEAN,
    execution_interval  TEXT,
    max_retry_count     INT,
    scheduled_at        TIMESTAMP,
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP,
    PRIMARY KEY (user_id, job_id)
) WITH CLUSTERING ORDER BY (job_id ASC);


CREATE TABLE IF NOT EXISTS task_schedule (
    next_execution_time BIGINT,
    ring_hash           BIGINT,
    job_id              UUID,
    is_recurring        BOOLEAN,
    execution_interval  TEXT,
    PRIMARY KEY (next_execution_time, ring_hash, job_id)
) WITH CLUSTERING ORDER BY (ring_hash ASC, job_id ASC)
  AND default_time_to_live = 604800;

-- Audit log
CREATE TABLE IF NOT EXISTS task_execution_history (
    job_id         UUID,
    execution_time TIMESTAMP,
    status         TEXT,
    retry_count    INT,
    error_message  TEXT,
    PRIMARY KEY (job_id, execution_time)
) WITH CLUSTERING ORDER BY (execution_time DESC)
  AND default_time_to_live = 7776000;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_jobs_job_id
    ON jobs (job_id);

CREATE INDEX IF NOT EXISTS idx_task_schedule_job_id
    ON task_schedule (job_id);
