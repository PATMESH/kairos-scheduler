package io.kairos.authservice.repository;

import io.kairos.authservice.entity.RefreshToken;
import io.kairos.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
    void deleteByToken(String token);

    int deleteByExpiresAtBefore(Instant expiresAt);
}
