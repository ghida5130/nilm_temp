package com.nilm.monitoring.subject.repository;

import com.nilm.monitoring.domain.PowerUsageHourly;
import com.nilm.monitoring.domain.PowerUsageHourlyId;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PowerUsageHourlyRepository extends JpaRepository<PowerUsageHourly, PowerUsageHourlyId> {
    @Query("select p from PowerUsageHourly p where p.id.householdId = :householdId "
            + "and p.id.hourStart >= :from and p.id.hourStart < :to order by p.id.hourStart")
    List<PowerUsageHourly> findRange(@Param("householdId") String householdId,
                                     @Param("from") Instant from, @Param("to") Instant to);
}
