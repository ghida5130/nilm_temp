package com.nilm.monitoring.staff.repository;

import com.nilm.monitoring.domain.StaffSetting;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffSettingRepository extends JpaRepository<StaffSetting, Long> {
    Optional<StaffSetting> findByStaffId(Long staffId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StaffSetting s where s.staffId = :staffId")
    Optional<StaffSetting> findByStaffIdForUpdate(@Param("staffId") Long staffId);
}
