package io.kairos.authservice.service;

import io.kairos.authservice.config.RequestContext;
import io.kairos.authservice.dto.request.LoginRequest;
import io.kairos.authservice.dto.request.RefreshTokenRequest;
import io.kairos.authservice.dto.request.SignupRequest;
import io.kairos.authservice.dto.response.AuthResponse;
import io.kairos.authservice.entity.RefreshToken;
import io.kairos.authservice.entity.User;
import io.kairos.authservice.exception.AuthenticationException;
import io.kairos.authservice.exception.DuplicateResourceException;
import io.kairos.authservice.exception.InvalidTokenException;
import io.kairos.authservice.kafka.event.UserCreatedEvent;
import io.kairos.authservice.kafka.producer.KafkaEventProducer;
import io.kairos.authservice.repository.UserRepository;
import io.kairos.authservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static io.kairos.authservice.constants.ApplicationConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final KafkaEventProducer kafkaEventProducer;
    private final RequestContext requestContext;

    @Value("${app.kafka.topics.user-events}")
    private String userEventsTopic;


    private static final long ACCESS_TOKEN_VALIDITY_MS = 10 * 60 * 1000; // 10 mins
    private static final String DEFAULT_ROLE = "USER";

    @Transactional
    public AuthResponse registerUser(SignupRequest request) {
        String correlationId = requestContext.getCorrelationId();

        log.debug("Registering new user: {}", request.getUsername());

        validateUniqueUser(request.getUsername(), request.getEmail());

        User user = createUser(request);
        User savedUser = userRepository.save(user);

        publishUserCreatedEvent(savedUser, correlationId);

        log.info("User registered successfully with ID: {}", savedUser.getId());
        return generateAuthResponse(savedUser);
    }

    @Transactional
    public AuthResponse authenticateUser(LoginRequest request) {
        log.debug("Authenticating user: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername())
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        log.info("User authenticated successfully: {}", user.getUsername());
        return generateAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refreshAccessToken(RefreshTokenRequest request) {
        log.debug("Refreshing access token");

        RefreshToken refreshToken = refreshTokenService.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn("Expired refresh token used");
            throw new InvalidTokenException("Refresh token has expired");
        }

        User user = refreshToken.getUser();
        String accessToken = generateAccessToken(user);
        Instant expiresAt = Instant.now().plusMillis(ACCESS_TOKEN_VALIDITY_MS);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(request.getRefreshToken())
                .expiresIn(ACCESS_TOKEN_VALIDITY_MS / 1000)
                .expiresAt(expiresAt)
                .user(mapToUserInfo(user))
                .build();
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        log.debug("Logging out user");
        refreshTokenService.deleteByToken(request.getRefreshToken());
        log.info("User logged out successfully");
    }

    private void validateUniqueUser(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Username already exists: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email already exists: " + email);
        }
    }

    private User createUser(SignupRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setCreatedAt(Instant.now());
        return user;
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenService.createToken(user);
        Instant expiresAt = Instant.now().plusMillis(ACCESS_TOKEN_VALIDITY_MS);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .expiresIn(ACCESS_TOKEN_VALIDITY_MS / 1000)
                .expiresAt(expiresAt)
                .user(mapToUserInfo(user))
                .build();
    }

    private String generateAccessToken(User user) {
        Map<String, Object> claims = Map.of(
                "role", DEFAULT_ROLE,
                "username", user.getUsername(),
                "email", user.getEmail()
        );
        return jwtUtil.generateAccessToken(
                user.getId().toString(),
                claims,
                ACCESS_TOKEN_VALIDITY_MS
        );
    }

    private AuthResponse.UserInfo mapToUserInfo(User user) {
        return AuthResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    // TODO: Need to implement transactional outbox pattern to maintain consistency (if kafka is down)
    private void publishUserCreatedEvent(User user, String correlationId) {
        try {
            UserCreatedEvent event = UserCreatedEvent.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .createdAt(user.getCreatedAt())
                    .build();

            kafkaEventProducer.publishEvent(
                    userEventsTopic,
                    USER_CREATED,
                    user.getId().toString(),
                    event,
                    correlationId
            );
        } catch (Exception e) {
            log.error("Error publishing user created event for user ID: {} [CorrelationId: {}]",
                    user.getId(), correlationId, e);
        }
    }
}