package com.nilm.monitoring.api;

import com.nilm.monitoring.dto.SubjectCreateRequest;
import com.nilm.monitoring.service.SubjectRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/monitoring/subjects")
public class SubjectController {

    private final SubjectRegistrationService registrationService;

    @PostMapping
    public ResponseEntity<Void> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubjectCreateRequest request
    ) {
        // jwt.getSubject(): 담당자 로그인 ID
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        registrationService.register(
                jwt.getSubject(),
                request
        );

        // 응답 Body 없이 상태 코드만 반환한다.
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .build();
    }

}
