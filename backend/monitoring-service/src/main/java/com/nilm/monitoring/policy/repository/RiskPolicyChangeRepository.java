package com.nilm.monitoring.policy.repository;

import com.nilm.monitoring.domain.RiskPolicyChange;
import com.nilm.monitoring.domain.RiskPolicyChangeStatus;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPolicyChangeRepository extends JpaRepository<RiskPolicyChange, UUID> {
    boolean existsByStaffIdAndStatus(Long staffId, RiskPolicyChangeStatus status);
    Optional<RiskPolicyChange> findByIdAndStaffId(UUID id, Long staffId);
    List<RiskPolicyChange> findByStatusOrderByCreatedAt(RiskPolicyChangeStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RiskPolicyChange c where c.id = :id")
    Optional<RiskPolicyChange> findByIdForUpdate(@Param("id") UUID id);
}
