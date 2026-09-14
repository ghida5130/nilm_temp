package com.nilm.monitoring.subject.service;

import com.nilm.monitoring.common.ServiceUnavailableException;
import com.nilm.monitoring.common.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class CandidateSearchRateLimiter {
    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;

    public CandidateSearchRateLimiter(StringRedisTemplate redis,
            @Value("${app.candidate-search.rate-limit:30}") int limit,
            @Value("${app.candidate-search.rate-window:PT1M}") Duration window) {
        this.redis = redis;
        this.limit = limit;
        this.window = window;
    }

    public void check(String authSub) {
        String key = "monitoring:rate:candidate:" + digest(authSub);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, window);
            if (count == null) throw new IllegalStateException("Redis increment returned null");
            if (count > limit) throw new TooManyRequestsException("대상자 검색 요청이 너무 많습니다.");
        } catch (TooManyRequestsException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceUnavailableException("대상자 검색 요청 제한 서비스를 사용할 수 없습니다.");
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash rate-limit key", e);
        }
    }
}
