package com.nilm.device.service;

import com.nilm.device.api.dto.GuardianDtos;
import com.nilm.device.common.DuplicateResourceException;
import com.nilm.device.common.NotFoundException;
import com.nilm.device.domain.HouseholdGuardian;
import com.nilm.device.repository.HouseholdGuardianRepository;
import com.nilm.device.repository.HouseholdRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GuardianService {

    private final HouseholdGuardianRepository guardianRepository;
    private final HouseholdRepository householdRepository;

    public GuardianService(HouseholdGuardianRepository guardianRepository,
                           HouseholdRepository householdRepository) {
        this.guardianRepository = guardianRepository;
        this.householdRepository = householdRepository;
    }

    @Transactional
    public GuardianDtos.Response add(String houseId, GuardianDtos.CreateRequest request) {
        requireHousehold(houseId);
        var id = new HouseholdGuardian.GuardianId(houseId, request.keycloakUserId());
        if (guardianRepository.existsById(id)) {
            throw new DuplicateResourceException("보호자 매핑", request.keycloakUserId());
        }
        HouseholdGuardian saved = guardianRepository.save(new HouseholdGuardian(
                houseId, request.keycloakUserId(), request.role(),
                request.notifyPhone(), request.notifyEnabled()));
        return GuardianDtos.Response.from(saved);
    }

    /** 모니터링 파트의 알림 수신자 조회가 이 목록을 사용한다. */
    public List<GuardianDtos.Response> list(String houseId) {
        requireHousehold(houseId);
        return guardianRepository.findByHouseIdOrderByRoleAsc(houseId).stream()
                .map(GuardianDtos.Response::from)
                .toList();
    }

    @Transactional
    public GuardianDtos.Response update(String houseId, UUID keycloakUserId,
                                        GuardianDtos.UpdateRequest request) {
        HouseholdGuardian guardian = findGuardian(houseId, keycloakUserId);
        guardian.update(request.role(), request.notifyPhone(), request.notifyEnabled());
        return GuardianDtos.Response.from(guardian);
    }

    @Transactional
    public void remove(String houseId, UUID keycloakUserId) {
        guardianRepository.delete(findGuardian(houseId, keycloakUserId));
    }

    private void requireHousehold(String houseId) {
        if (!householdRepository.existsById(houseId)) {
            throw new NotFoundException("가구", houseId);
        }
    }

    private HouseholdGuardian findGuardian(String houseId, UUID keycloakUserId) {
        return guardianRepository.findById(new HouseholdGuardian.GuardianId(houseId, keycloakUserId))
                .orElseThrow(() -> new NotFoundException("보호자 매핑", keycloakUserId));
    }
}
