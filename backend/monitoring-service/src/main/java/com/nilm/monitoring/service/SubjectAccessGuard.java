package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.repository.ManagerRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/monitoring/subjects/{subjectId}/**} 경로가 공통으로 쓰는 접근 판정.
 * 배정된 담당자와 대상자 본인만 읽을 수 있다(IDOR 차단).
 */
@Component
@RequiredArgsConstructor
public class SubjectAccessGuard {

    private final SubjectRepository subjects;
    private final ManagerRepository managers;

    @Transactional(readOnly = true)
    public Subject requireReadable(String authSub, Long subjectId) {
        if (authSub == null || authSub.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대상자 정보를 찾을 수 없습니다."
                ));

        if (authSub.equals(subject.getAuthSub())) {
            return subject;
        }

        boolean assignedManager = managers.findByAuthSub(authSub)
                .map(manager -> Objects.equals(manager.getId(), subject.getManagerId()))
                .orElse(false);
        if (!assignedManager) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "배정된 담당자만 대상자 정보를 조회할 수 있습니다."
            );
        }
        return subject;
    }
}
