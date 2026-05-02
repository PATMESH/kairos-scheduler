package io.kairos.execution.repo;

import io.kairos.execution.model.Job;
import io.kairos.execution.model.JobKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface JobRepository extends ReactiveCassandraRepository<Job, JobKey> {
    Flux<Job> findByKeyUserId(UUID userId);
    Mono<Job> findByKeyJobId(UUID jobId);
}