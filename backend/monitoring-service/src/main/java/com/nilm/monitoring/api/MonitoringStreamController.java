package com.nilm.monitoring.api;

import com.nilm.monitoring.service.SubjectStatusStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 담당자 대시보드 실시간 조회.
 * 토큰 주인이 담당자이고, 그 담당자에게 배정된 대상자 전체의 변화만 흘러나온다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitoring")
public class MonitoringStreamController {

    private final SubjectStatusStreamService streamService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        String authSub = jwt == null ? null : jwt.getSubject();
        return streamService.subscribe(authSub);
    }
}
