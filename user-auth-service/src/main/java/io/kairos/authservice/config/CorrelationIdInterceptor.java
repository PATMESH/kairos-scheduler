package io.kairos.authservice.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

import static io.kairos.authservice.constants.ApplicationConstants.CORRELATION_ID_HEADER;
import static io.kairos.authservice.constants.ApplicationConstants.USER_ID_HEADER;

@Component
@Slf4j
@RequiredArgsConstructor
public class CorrelationIdInterceptor implements HandlerInterceptor {

    private final RequestContext requestContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        String userId = request.getHeader(USER_ID_HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        requestContext.setCorrelationId(correlationId);
        requestContext.setUserId(userId);
        MDC.put("correlationId", correlationId);
        if (userId != null) {
            MDC.put("userId", userId);
        }

        log.debug("Request started - Method: {}, URI: {}, CorrelationId: {}",
                request.getMethod(), request.getRequestURI(), correlationId);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.clear();
    }
}
