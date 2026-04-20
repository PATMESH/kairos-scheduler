package io.kairos.jobservice.repository;

import io.kairos.jobservice.model.Job;
import io.kairos.jobservice.model.JobKey;
import org.springframework.data.cassandra.repository.ReactiveCassandraRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface JobRepository extends ReactiveCassandraRepository<Job, JobKey> {
    Flux<Job> findByKeyUserId(UUID userId);
}