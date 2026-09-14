package com.nilm.monitoring.common.api;

import jakarta.validation.Valid;
import com.nilm.monitoring.common.dto.EchoRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 실행 환경 확인용 최소 API.
 * - GET  /api/monitoring/ping : 서비스 동작 확인
 * - POST /api/monitoring/echo : @Valid 검증과 공통 오류 응답 확인
 */
@RestController
@RequestMapping("/api/monitoring")
public class PingController {

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("service", "monitoring-service", "status", "ok");
    }

    @GetMapping("/admin/ping")
    public Map<String, String> adminPing() {
        return Map.of("service", "monitoring-service", "admin", "ok");
    }

    @PostMapping("/echo")
    public Map<String, String> echo(@Valid @RequestBody EchoRequest request) {
        return Map.of("echo", request.message());
    }

}
