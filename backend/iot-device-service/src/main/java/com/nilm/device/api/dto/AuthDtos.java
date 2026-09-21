package com.nilm.device.api.dto;

import com.nilm.device.domain.UserProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 100)
            String email,
            @NotBlank @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
            String password,
            @NotBlank @Size(max = 50)
            String displayName,
            @Size(max = 20)
            String phone,
            /** 기관 소속이면 입력 — 복지사 가입으로 간주되어 담당자 명단에 오른다 */
            @Size(max = 100)
            String organization
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email
            String email,
            @NotBlank
            String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank
            String refreshToken
    ) {
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            Integer expiresIn,
            String tokenType
    ) {
        public static TokenResponse of(String access, String refresh, Integer expiresIn) {
            return new TokenResponse(access, refresh, expiresIn, "Bearer");
        }
    }

    public record ProfileResponse(
            UUID userId,
            String email,
            String displayName,
            String phone,
            String organization,
            UserProfile.Status status
    ) {
        public static ProfileResponse from(UserProfile p) {
            return new ProfileResponse(p.getKeycloakUserId(), p.getEmail(), p.getDisplayName(),
                    p.getPhone(), p.getOrganization(), p.getStatus());
        }
    }

    /** 로그인 직후 화면 구성의 기준 — 내 프로필 + 내가 접근 가능한 가구 목록 */
    public record MeResponse(
            ProfileResponse profile,
            List<MemberDtos.MyHousehold> households
    ) {
    }
}
