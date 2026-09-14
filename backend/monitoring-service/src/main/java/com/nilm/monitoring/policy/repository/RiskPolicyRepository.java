package com.nilm.monitoring.policy.repository;

import com.nilm.monitoring.domain.RiskPolicy;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, Long> {
    List<RiskPolicy> findAllByOrderByPolicyCodeAscVersionDesc();
}
