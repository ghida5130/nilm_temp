package com.nilm.device.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserProfileTest {

    private static final UUID USER = UUID.randomUUID();

    private UserProfile profile(String organization) {
        return new UserProfile(USER, "user@nilm.test", "홍길동", "010-1234-5678", organization);
    }

    @Test
    @DisplayName("기관 소속이 있으면 승인 대기(PENDING)로 시작한다")
    void staffStartsPending() {
        assertEquals(UserProfile.Status.PENDING, profile("행복구 복지센터").getStatus());
    }

    @Test
    @DisplayName("기관 소속이 없으면 바로 활성(ACTIVE)이다")
    void generalUserStartsActive() {
        assertEquals(UserProfile.Status.ACTIVE, profile(null).getStatus());
    }

    @Test
    @DisplayName("공백만 입력한 소속은 소속 없음으로 본다 — 공백으로 승인 절차를 우회할 수 없다")
    void blankOrganizationIsNotStaff() {
        assertEquals(UserProfile.Status.ACTIVE, profile("   ").getStatus());
    }

    @Test
    @DisplayName("승인하면 활성, 정지하면 중단 상태가 된다")
    void approveAndSuspend() {
        UserProfile p = profile("행복구 복지센터");

        p.approve();
        assertEquals(UserProfile.Status.ACTIVE, p.getStatus());

        p.suspend();
        assertEquals(UserProfile.Status.SUSPENDED, p.getStatus());
    }
}
