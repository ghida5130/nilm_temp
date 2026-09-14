package com.nilm.device.repository;

import com.nilm.device.domain.HouseholdGuardian;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdGuardianRepository
        extends JpaRepository<HouseholdGuardian, HouseholdGuardian.GuardianId> {

    List<HouseholdGuardian> findByHouseIdOrderByRoleAsc(String houseId);
}
