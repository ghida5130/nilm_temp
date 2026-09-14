package com.nilm.monitoring.outbox.repository;

import com.nilm.monitoring.domain.UiOutbox;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UiOutboxRepository extends JpaRepository<UiOutbox, Long> {

    List<UiOutbox> findByPublishedAtIsNullOrderByOutboxId(Pageable pageable);

    List<UiOutbox> findByOutboxIdGreaterThanOrderByOutboxId(long afterId, Pageable pageable);

    @Query("select o from UiOutbox o where o.outboxId > :afterId "
            + "and (o.recipientUserId is null or o.recipientUserId = :userId) order by o.outboxId")
    List<UiOutbox> findReplayForAdmin(@Param("userId") String userId,
                                      @Param("afterId") long afterId, Pageable pageable);

    @Query("""
            select o from UiOutbox o
             where o.outboxId > :afterId
               and (o.recipientUserId = :userId or (o.recipientUserId is null and (exists (
                   select h.id from HouseholdAccess h
                    where h.householdId = o.householdId
                      and h.userId = :userId
               ) or exists (
                   select a.id from StaffSubjectAssignment a, StaffProfile p, CareSubject s
                    where a.staffId = p.id and a.subjectId = s.id
                      and p.authSub = :userId
                      and p.status = com.nilm.monitoring.domain.StaffStatus.ACTIVE
                      and a.unassignedAt is null
                      and s.householdId = o.householdId
               ))))
             order by o.outboxId
            """)
    List<UiOutbox> findReplayForUser(@Param("userId") String userId,
                                     @Param("afterId") long afterId,
                                     Pageable pageable);
}
