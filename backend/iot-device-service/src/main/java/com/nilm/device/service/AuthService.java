package com.nilm.device.service;

import com.nilm.device.api.dto.AuthDtos;
import com.nilm.device.common.DuplicateResourceException;
import com.nilm.device.common.NotFoundException;
import com.nilm.device.domain.UserProfile;
import com.nilm.device.repository.UserProfileRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입·로그인 — 신원 자체는 Keycloak이 맡고, 이 서비스는
 * "가입 후처리"(서비스 프로필 저장)와 토큰 발급 대행만 담당한다.
 * 비밀번호는 Keycloak으로 통과만 하고 어디에도 저장하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final KeycloakAdminClient keycloak;
    private final UserProfileRepository profileRepository;
    private final MembershipService membershipService;

    public AuthService(KeycloakAdminClient keycloak,
                       UserProfileRepository profileRepository,
                       MembershipService membershipService) {
        this.keycloak = keycloak;
        this.profileRepository = profileRepository;
        this.membershipService = membershipService;
    }

    /**
     * 가입: Keycloak 계정 생성 후 서비스 프로필 저장.
     * 프로필 저장이 실패하면 생성된 Keycloak 계정을 되돌린다(보상).
     * 기관 소속을 입력한 가입은 PENDING으로 시작해 관리자 승인이 필요하다.
     */
    @Transactional
    public AuthDtos.ProfileResponse signup(AuthDtos.SignupRequest request) {
        if (profileRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("계정", request.email());
        }
        UUID userId = keycloak.createUser(request.email(), request.password(), request.displayName());
        try {
            UserProfile profile = profileRepository.save(new UserProfile(
                    userId, request.email(), request.displayName(),
                    request.phone(), request.organization()));
            return AuthDtos.ProfileResponse.from(profile);
        } catch (RuntimeException e) {
            log.error("프로필 저장 실패 — Keycloak 계정 보상 삭제: {}", userId, e);
            keycloak.deleteUser(userId);
            throw e;
        }
    }

    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        var token = keycloak.login(request.email(), request.password());
        return AuthDtos.TokenResponse.of(token.accessToken(), token.refreshToken(), token.expiresIn());
    }

    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        var token = keycloak.refresh(request.refreshToken());
        return AuthDtos.TokenResponse.of(token.accessToken(), token.refreshToken(), token.expiresIn());
    }

    /** 로그인 직후 화면 구성 기준 — 내 프로필 + 접근 가능한 가구 목록 */
    public AuthDtos.MeResponse me(UUID userId) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("프로필", userId));
        return new AuthDtos.MeResponse(
                AuthDtos.ProfileResponse.from(profile),
                membershipService.myHouseholds(userId));
    }
}
