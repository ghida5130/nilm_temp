package com.nilm.monitoring.staff.repository;

import com.nilm.monitoring.domain.StaffProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {
    Optional<StaffProfile> findByAuthSub(String authSub);
}
