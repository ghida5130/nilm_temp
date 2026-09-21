package com.nilm.device.service;

import com.nilm.device.api.dto.AuthDtos;
import com.nilm.device.common.DuplicateResourceException;
import com.nilm.device.common.InvalidOperationException;
import com.nilm.device.common.NotFoundException;
import com.nilm.device.domain.ManagerRegistrationOutbox;
import com.nilm.device.domain.UserProfile;
import com.nilm.device.repository.ManagerRegistrationOutboxRepository;
import com.nilm.device.repository.UserProfileRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
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
    private final ManagerRegistrationOutboxRepository outboxRepository;
    private final MembershipService membershipService;

    public AuthService(KeycloakAdminClient keycloak,
                       UserProfileRepository profileRepository,
                       ManagerRegistrationOutboxRepository outboxRepository,
                       MembershipService membershipService) {
        this.keycloak = keycloak;
        this.profileRepository = profileRepository;
        this.outboxRepository = outboxRepository;
        this.membershipService = membershipService;
    }

    /**
     * 가입: Keycloak 계정 생성 후 서비스 프로필 저장.
     * 프로필 저장이 실패하면 생성된 Keycloak 계정을 되돌린다(보상).
     *
     * <p>기관 소속을 입력한 가입은 복지사로 보고 담당자 등록 이벤트를 같은 트랜잭션에
     * 적어둔다. 그래야 monitoring 쪽 담당자 명단에 올라 대상자를 등록할 수 있다.
     * 발행은 릴레이가 맡으므로 Kafka가 잠시 죽어 있어도 가입 자체는 성공한다.
     */
    @Transactional
    public AuthDtos.ProfileResponse signup(AuthDtos.SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (profileRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("계정", email);
        }
        UUID userId = keycloak.createUser(email, request.password(), request.displayName());
        try {
            UserProfile profile = profileRepository.save(new UserProfile(
                    userId, email, request.displayName(),
                    request.phone(), request.organization()));
            enqueueManagerRegistration(profile);
            return AuthDtos.ProfileResponse.from(profile);
        } catch (RuntimeException e) {
            // 예외 자체는 상위 핸들러가 기록한다. 여기서는 보상 조치만 남긴다.
            log.warn("프로필 저장 실패로 Keycloak 계정을 보상 삭제합니다: {}", userId);
            keycloak.deleteUser(userId);
            throw e;
        }
    }

    /** 기관 소속이 없는 가입(본인·보호자)은 담당자가 아니므로 내보낼 것이 없다. */
    private void enqueueManagerRegistration(UserProfile profile) {
        String organization = profile.getOrganization();
        if (organization == null || organization.isBlank()) {
            return;
        }
        outboxRepository.save(new ManagerRegistrationOutbox(profile, OffsetDateTime.now()));
    }

    /** 로그인 — 가입 때와 같은 규칙으로 정규화해야 대소문자를 달리 입력해도 들어온다. */
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        var token = keycloak.login(normalizeEmail(request.email()), request.password());
        return AuthDtos.TokenResponse.of(token.accessToken(), token.refreshToken(), token.expiresIn());
    }

    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        var token = keycloak.refresh(request.refreshToken());
        return AuthDtos.TokenResponse.of(token.accessToken(), token.refreshToken(), token.expiresIn());
    }

    /**
     * 로그아웃 — 클라이언트가 저장소에서 토큰을 지우는 것만으로는 부족하다.
     * refresh token이 Keycloak에 살아 있으면 유출된 토큰으로 계속 갱신할 수 있다.
     */
    public void logout(AuthDtos.LogoutRequest request) {
        keycloak.logout(request.refreshToken());
    }

    /** 가입 폼용 — 다 입력해 제출한 뒤에야 중복을 알게 되는 상황을 막는다. */
    public AuthDtos.EmailAvailability checkEmail(String email) {
        String normalized = normalizeEmail(email);
        return new AuthDtos.EmailAvailability(
                normalized, !profileRepository.existsByEmail(normalized));
    }

    /**
     * 이메일 정규화 — Keycloak은 사용자명을 소문자로 저장하는데 우리 컬럼은 대소문자를
     * 구분한다. 정규화하지 않으면 {@code Kim@a.com}이 로컬 중복 검사를 통과한 뒤
     * Keycloak에서 409로 막혀, 중복 확인 결과와 가입 결과가 어긋난다.
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 프로필 수정 — 이름은 Keycloak에도 반영한다.
     *
     * <p>Keycloak을 먼저 호출한다. 반대 순서면 Keycloak 실패 시
     * "우리 DB만 새 이름, Keycloak은 옛 이름"인 상태가 남는다.
     */
    @Transactional
    public AuthDtos.ProfileResponse updateProfile(UUID userId,
                                                  AuthDtos.UpdateProfileRequest request) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("프로필", userId));
        if (request.displayName() != null && !request.displayName().isBlank()) {
            keycloak.updateDisplayName(userId, request.displayName());
        }
        profile.updateProfile(request.displayName(), request.phone());
        return AuthDtos.ProfileResponse.from(profile);
    }

    /**
     * 비밀번호 변경.
     *
     * <p>현재 비밀번호는 Keycloak 로그인을 시도해 확인한다 — 우리는 해시를 갖고 있지
     * 않으므로 실제로 통과하는지 물어보는 것이 유일한 검증 방법이다.
     *
     * <p>변경 후 모든 세션을 끊는다. 이전 비밀번호로 발급된 토큰이 살아 있으면
     * 비밀번호를 바꾼 의미가 없기 때문이며, 호출자 본인도 다시 로그인해야 한다.
     */
    public void changePassword(UUID userId, AuthDtos.ChangePasswordRequest request) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("프로필", userId));
        if (request.currentPassword().equals(request.newPassword())) {
            throw new InvalidOperationException("현재 비밀번호와 다른 비밀번호를 입력해 주세요");
        }
        try {
            keycloak.login(profile.getEmail(), request.currentPassword());
        } catch (InvalidOperationException e) {
            throw new InvalidOperationException("현재 비밀번호가 올바르지 않습니다");
        }
        keycloak.resetPassword(userId, request.newPassword());
        keycloak.logoutAllSessions(userId);
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
