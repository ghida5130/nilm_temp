package com.nilm.monitoring.incident.repository;

import com.nilm.monitoring.domain.HouseholdAccess;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HouseholdAccessRepository extends JpaRepository<HouseholdAccess, Long> {

    boolean existsByHouseholdIdAndUserId(String householdId, String userId);

    @Query("select h.userId from HouseholdAccess h where h.householdId = :householdId")
    List<String> findUserIdsByHouseholdId(@Param("householdId") String householdId);
}
