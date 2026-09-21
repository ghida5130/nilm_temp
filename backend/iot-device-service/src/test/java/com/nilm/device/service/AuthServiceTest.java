package com.nilm.device.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nilm.device.api.dto.AuthDtos;
import com.nilm.device.common.InvalidOperationException;
import com.nilm.device.common.NotFoundException;
import com.nilm.device.domain.UserProfile;
import com.nilm.device.repository.ManagerRegistrationOutboxRepository;
import com.nilm.device.repository.UserProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 인증 서비스 — Keycloak 호출 순서와 실패 시 처리가 검증 대상이다.
 * 토큰 발급 자체는 Keycloak 책임이므로 여기서 확인하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final String EMAIL = "user@nilm.test";

    @Mock
    private KeycloakAdminClient keycloak;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private ManagerRegistrationOutboxRepository outboxRepository;
    @Mock
    private MembershipService membershipService;

    @InjectMocks
    private AuthService authService;

    private UserProfile profile() {
        return new UserProfile(USER, EMAIL, "홍길동", "010-1234-5678", null);
    }

    // ── 이메일 중복 확인 ────────────────────────────────────

    @Test
    @DisplayName("대소문자를 달리 입력해도 같은 이메일로 판정한다")
    void checkEmailNormalizesCase() {
        when(profileRepository.existsByEmail(EMAIL)).thenReturn(true);

        AuthDtos.EmailAvailability result = authService.checkEmail("  User@NILM.test  ");

        assertEquals(EMAIL, result.email(), "정규화된 형태를 돌려줘야 한다");
        assertFalse(result.available());
    }

    @Test
    @DisplayName("등록되지 않은 이메일은 사용 가능하다")
    void checkEmailAvailable() {
        when(profileRepository.existsByEmail(EMAIL)).thenReturn(false);

        assertTrue(authService.checkEmail(EMAIL).available());
    }

    // ── 로그아웃 ──────────────────────────────────────────

    @Test
    @DisplayName("로그아웃은 refresh token을 Keycloak에서 폐기한다")
    void logoutRevokesRefreshToken() {
        authService.logout(new AuthDtos.LogoutRequest("refresh-token"));

        verify(keycloak).logout("refresh-token");
    }

    // ── 프로필 수정 ────────────────────────────────────────

    @Test
    @DisplayName("이름을 바꾸면 Keycloak을 먼저 갱신한 뒤 로컬 프로필에 반영한다")
    void updateProfileSyncsKeycloakFirst() {
        UserProfile profile = profile();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));

        authService.updateProfile(USER, new AuthDtos.UpdateProfileRequest("김철수", "010-9999-9999"));

        InOrder order = Mockito.inOrder(keycloak);
        order.verify(keycloak).updateDisplayName(USER, "김철수");
        assertEquals("김철수", profile.getDisplayName());
        assertEquals("010-9999-9999", profile.getPhone());
    }

    @Test
    @DisplayName("이름을 보내지 않으면 Keycloak을 호출하지 않는다")
    void updateProfileSkipsKeycloakWhenNameUnchanged() {
        UserProfile profile = profile();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));

        authService.updateProfile(USER, new AuthDtos.UpdateProfileRequest(null, "010-9999-9999"));

        verify(keycloak, never()).updateDisplayName(any(), anyString());
        assertEquals("홍길동", profile.getDisplayName());
        assertEquals("010-9999-9999", profile.getPhone());
    }

    @Test
    @DisplayName("없는 프로필은 404다")
    void updateProfileNotFound() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                authService.updateProfile(USER, new AuthDtos.UpdateProfileRequest("김철수", null)));
    }

    // ── 비밀번호 변경 ──────────────────────────────────────

    @Test
    @DisplayName("현재 비밀번호를 확인한 뒤 재설정하고 모든 세션을 끊는다")
    void changePasswordVerifiesThenResetsAndLogsOut() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile()));

        authService.changePassword(USER, new AuthDtos.ChangePasswordRequest("old-pass", "new-pass"));

        InOrder order = Mockito.inOrder(keycloak);
        order.verify(keycloak).login(EMAIL, "old-pass");
        order.verify(keycloak).resetPassword(USER, "new-pass");
        order.verify(keycloak).logoutAllSessions(USER);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 재설정하지 않는다")
    void changePasswordRejectsWrongCurrent() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile()));
        when(keycloak.login(eq(EMAIL), eq("wrong")))
                .thenThrow(new InvalidOperationException("이메일 또는 비밀번호가 올바르지 않습니다"));

        InvalidOperationException e = assertThrows(InvalidOperationException.class, () ->
                authService.changePassword(USER, new AuthDtos.ChangePasswordRequest("wrong", "new-pass")));

        assertTrue(e.getMessage().contains("현재 비밀번호"));
        verify(keycloak, never()).resetPassword(any(), anyString());
        verify(keycloak, never()).logoutAllSessions(any());
    }

    @Test
    @DisplayName("같은 비밀번호로는 바꿀 수 없다 — 확인 요청조차 보내지 않는다")
    void changePasswordRejectsSameValue() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile()));

        assertThrows(InvalidOperationException.class, () ->
                authService.changePassword(USER, new AuthDtos.ChangePasswordRequest("same", "same")));

        verify(keycloak, never()).login(anyString(), anyString());
        verify(keycloak, never()).resetPassword(any(), anyString());
    }
}
