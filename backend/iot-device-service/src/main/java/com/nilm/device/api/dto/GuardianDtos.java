package com.nilm.device.api.dto;

import com.nilm.device.domain.HouseholdGuardian;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class GuardianDtos {

    private GuardianDtos() {
    }

    public record CreateRequest(
            @NotNull
            UUID keycloakUserId,
            HouseholdGuardian.Role role,
            @NotBlank @Size(max = 20)
            String notifyPhone,
            Boolean notifyEnabled
    ) {
    }

    public record UpdateRequest(
            HouseholdGuardian.Role role,
            @Size(max = 20)
            String notifyPhone,
            Boolean notifyEnabled
    ) {
    }

    public record Response(
            String houseId,
            UUID keycloakUserId,
            HouseholdGuardian.Role role,
            String notifyPhone,
            boolean notifyEnabled
    ) {
        public static Response from(HouseholdGuardian g) {
            return new Response(g.getHouseId(), g.getKeycloakUserId(), g.getRole(),
                    g.getNotifyPhone(), g.isNotifyEnabled());
        }
    }
}
