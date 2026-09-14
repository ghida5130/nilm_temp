package com.nilm.monitoring.common.dto;

import jakarta.validation.constraints.NotBlank;

public record EchoRequest(
        @NotBlank(message = "message는 비어 있을 수 없습니다.") String message
) {
}
