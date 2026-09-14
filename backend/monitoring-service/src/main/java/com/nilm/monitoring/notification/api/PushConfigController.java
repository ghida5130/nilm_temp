package com.nilm.monitoring.notification.api;

import com.nilm.monitoring.notification.dto.PushConfigResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/push-config")
public class PushConfigController {
    private final boolean enabled;
    private final String publicKey;

    public PushConfigController(@Value("${app.web-push.enabled:false}") boolean enabled,
                                @Value("${app.web-push.vapid-public-key:}") String publicKey) {
        this.enabled = enabled && !publicKey.isBlank();
        this.publicKey = publicKey;
    }

    @GetMapping
    public PushConfigResponse get() {
        return new PushConfigResponse(enabled, enabled ? publicKey : null);
    }
}
