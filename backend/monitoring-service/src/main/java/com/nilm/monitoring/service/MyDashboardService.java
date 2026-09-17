package com.nilm.monitoring.service;

import com.nilm.monitoring.domain.Subject;
import com.nilm.monitoring.dto.MyDashboardResponse;
import com.nilm.monitoring.repository.ManagerRepository;
import com.nilm.monitoring.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MyDashboardService {

    private final SubjectRepository subjects;
    private final ManagerRepository managers;

    @Transactional(readOnly = true)
    public MyDashboardResponse getMyDashboard(String subjectAuthSub) {
        if (subjectAuthSub == null || subjectAuthSub.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        Subject subject = subjects.findByAuthSub(subjectAuthSub)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "대상자 정보를 찾을 수 없습니다."
                ));

        MyDashboardResponse.ManagerSummary manager = subject.getManagerId() == null
                ? null
                : managers.findById(subject.getManagerId())
                        .map(value -> new MyDashboardResponse.ManagerSummary(
                                value.getName(),
                                value.getPhone()
                        ))
                        .orElse(null);

        boolean awayEnabled = !subject.isMonitoringEnabled();
        MyDashboardResponse.AwayMode awayMode = new MyDashboardResponse.AwayMode(
                awayEnabled,
                awayEnabled ? subject.getAwayStartedAt() : null,
                awayEnabled ? subject.getAwayUntil() : null
        );

        return new MyDashboardResponse(
                subject.getId().toString(),
                subject.getName(),
                awayMode,
                manager
        );
    }
}
