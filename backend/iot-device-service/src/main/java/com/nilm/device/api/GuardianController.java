package com.nilm.device.api;

import com.nilm.device.api.dto.GuardianDtos;
import com.nilm.device.service.GuardianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Guardian", description = "가구-보호자 매핑 관리 (알림 수신자)")
@RestController
@RequestMapping("/api/devices/households/{houseId}/guardians")
public class GuardianController {

    private final GuardianService guardianService;

    public GuardianController(GuardianService guardianService) {
        this.guardianService = guardianService;
    }

    @Operation(summary = "보호자 매핑 등록",
            description = "Keycloak 사용자를 가구의 보호자로 연결. 신원 정보는 저장하지 않고 UUID만 참조.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuardianDtos.Response add(@PathVariable String houseId,
                                     @Valid @RequestBody GuardianDtos.CreateRequest request) {
        return guardianService.add(houseId, request);
    }

    @Operation(summary = "가구의 보호자 목록 조회", description = "PRIMARY 우선 정렬 — 알림 발송 순서 기준.")
    @GetMapping
    public List<GuardianDtos.Response> list(@PathVariable String houseId) {
        return guardianService.list(houseId);
    }

    @Operation(summary = "보호자 매핑 수정", description = "역할·연락처·알림 수신 여부 부분 수정 (null 필드는 유지).")
    @PatchMapping("/{keycloakUserId}")
    public GuardianDtos.Response update(@PathVariable String houseId,
                                        @PathVariable UUID keycloakUserId,
                                        @Valid @RequestBody GuardianDtos.UpdateRequest request) {
        return guardianService.update(houseId, keycloakUserId, request);
    }

    @Operation(summary = "보호자 매핑 해제")
    @DeleteMapping("/{keycloakUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String houseId, @PathVariable UUID keycloakUserId) {
        guardianService.remove(houseId, keycloakUserId);
    }
}
