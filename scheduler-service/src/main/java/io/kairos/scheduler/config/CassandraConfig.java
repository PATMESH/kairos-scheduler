package io.kairos.scheduler.config;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import org.springframework.boot.cassandra.autoconfigure.CassandraProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;

@Configuration
public class CassandraConfig {

    @Bean
    public CqlSession cassandraSession(CassandraProperties properties) {

        int maxAttempts = 10;
        int delayMs = 3000;

        var contactPoints = properties.getContactPoints().stream()
                .map(a -> InetSocketAddress.createUnresolved(a, properties.getPort()))
                .toList();

        for (int i = 1; i <= maxAttempts; i++) {
            try {
                var builder = CqlSession.builder()
                        .addContactPoints(contactPoints)
                        .withLocalDatacenter(properties.getLocalDatacenter());

                var keyspace = properties.getKeyspaceName();
                if (keyspace != null && !keyspace.isBlank()) {
                    builder.withKeyspace(CqlIdentifier.fromCql(keyspace));
                }

                return builder.build();

            } catch (Exception e) {
                if (i == maxAttempts) throw e;
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {}
            }
        }

        throw new IllegalStateException("Unable to connect to Cassandra");
    }
}