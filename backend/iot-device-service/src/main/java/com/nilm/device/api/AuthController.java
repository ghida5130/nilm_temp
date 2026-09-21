package com.nilm.device.api;

import com.nilm.device.api.dto.AuthDtos;
import com.nilm.device.security.CurrentUser;
import com.nilm.device.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "가입·로그인 (신원은 Keycloak, 서비스 프로필은 device_db)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @Operation(summary = "회원가입",
            description = "Keycloak 계정 생성 + 서비스 프로필 저장. organization을 입력하면 "
                    + "기관 담당자 가입으로 간주되어 모니터링의 담당자 명단에 등록된다.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDtos.ProfileResponse signup(@Valid @RequestBody AuthDtos.SignupRequest request) {
        return authService.signup(request);
    }

    @Operation(summary = "로그인", description = "Keycloak 토큰 발급 대행. 비밀번호는 저장하지 않는다.")
    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "내 정보",
            description = "프로필 + 내가 접근 가능한 가구 목록(관계 포함). 로그인 직후 화면 구성의 기준.")
    @GetMapping("/me")
    public AuthDtos.MeResponse me() {
        return authService.me(currentUser.id());
    }
}
