package io.kairos.authservice.service;

import io.kairos.authservice.entity.RefreshToken;
import io.kairos.authservice.entity.User;
import io.kairos.authservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;

    @Value("${jwt.refresh-token.expiration:604800}")
    private long refreshTokenValiditySeconds;

    private static final int TOKEN_BYTE_LENGTH = 64;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional
    public RefreshToken createToken(User user) {
        log.debug("Creating refresh token for user: {}", user.getUsername());

        deleteByUser(user);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(generateSecureToken())
                .expiresAt(calculateExpirationTime())
                .createdAt(Instant.now())
                .build();

        RefreshToken saved = repository.save(refreshToken);
        log.info("Refresh token created for user ID: {}", user.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<RefreshToken> findByToken(String token) {
        log.debug("Finding refresh token");
        return repository.findByToken(token);
    }

    @Transactional
    public void deleteByToken(String token) {
        log.debug("Deleting refresh token");
        repository.deleteByToken(token);
    }

    @Transactional
    public void deleteByUser(User user) {
        log.debug("Deleting all refresh tokens for user: {}", user.getUsername());
        repository.deleteByUser(user);
    }

    @Transactional
    public int deleteExpiredTokens() {
        log.info("Cleaning up expired refresh tokens");
        Instant now = Instant.now();
        int deleted = repository.deleteByExpiresAtBefore(now);
        log.info("Deleted {} expired refresh tokens", deleted);
        return deleted;
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private Instant calculateExpirationTime() {
        return Instant.now().plusSeconds(refreshTokenValiditySeconds);
    }
}