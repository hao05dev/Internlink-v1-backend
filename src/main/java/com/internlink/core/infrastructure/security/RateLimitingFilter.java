package com.internlink.core.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internlink.core.shared.api.ApiResponse;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Đảm nhiệm duy nhất 1 nhiệm vụ: Lọc và chặn các yêu cầu vượt quá hạn ngạch Rate Limit (HTTP 429).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIps;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        RateLimiterService.LimitCategory category = resolveCategory(path);

        if (category != null) {
            String clientIp = clientIps.resolve(request);
            ConsumptionProbe probe = rateLimiterService.tryConsume(clientIp, category);

            if (!probe.isConsumed()) {
                long waitSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
                log.warn("Rate limit vượt ngưỡng cho IP [{}] tại URI [{}]. Cần chờ: {}s", clientIp, path, waitSeconds);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Retry-After", String.valueOf(waitSeconds));

                ApiResponse<Void> errorResponse = ApiResponse.error(
                        String.format("Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau %d giây.", waitSeconds)
                );

                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private RateLimiterService.LimitCategory resolveCategory(String path) {
        if (path.startsWith("/api/v1/auth/login")) {
            return RateLimiterService.LimitCategory.LOGIN;
        }
        if (path.startsWith("/api/v1/auth/forgot-password") || path.startsWith("/api/v1/auth/register")) {
            return RateLimiterService.LimitCategory.RECOVERY;
        }
        if (path.startsWith("/api/v1/auth/google") || path.startsWith("/api/v1/auth/reset-password")) {
            return RateLimiterService.LimitCategory.LOGIN;
        }
        if (path.startsWith("/api/v1/auth/refresh")) {
            return RateLimiterService.LimitCategory.DEFAULT;
        }
        if (path.startsWith("/api/v1/documents/uploads")) {
            return RateLimiterService.LimitCategory.UPLOAD;
        }
        if (path.startsWith("/api/v1/ai-matching") || path.startsWith("/api/v1/ai-workspace")
                || path.startsWith("/api/v1/assistant")) {
            return RateLimiterService.LimitCategory.AI;
        }
        return null;
    }

}
