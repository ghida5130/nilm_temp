package com.nilm.monitoring.subject.repository;

import com.nilm.monitoring.domain.StaffRegionAccess;
import com.nilm.monitoring.domain.StaffRegionAccessId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRegionAccessRepository extends JpaRepository<StaffRegionAccess, StaffRegionAccessId> {
    @Query("select r.id.regionCode from StaffRegionAccess r where r.id.staffId = :staffId")
    List<String> findRegionCodes(@Param("staffId") Long staffId);
}
