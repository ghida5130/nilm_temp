package com.nilm.monitoring.subject.api;

import com.nilm.monitoring.common.BadRequestException;
import com.nilm.monitoring.common.dto.CursorPage;
import com.nilm.monitoring.security.CurrentUserService;
import com.nilm.monitoring.subject.dto.AnalysisEventResponse;
import com.nilm.monitoring.subject.dto.PowerUsageResponse;
import com.nilm.monitoring.subject.dto.SubjectCardResponse;
import com.nilm.monitoring.subject.dto.SubjectDetailResponse;
import com.nilm.monitoring.subject.dto.SubjectSnapshotResponse;
import com.nilm.monitoring.subject.service.SubjectQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/monitoring/subjects")
public class SubjectController {
    private static final Set<String> LEVELS = Set.of("NORMAL", "WARNING", "DANGER", "UNKNOWN");
    private final SubjectQueryService service;
    private final CurrentUserService currentUser;

    public SubjectController(SubjectQueryService service, CurrentUserService currentUser) {
        this.service = service; this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<CursorPage<SubjectCardResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(defaultValue = "RISK_DESC") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        if (riskLevel != null && !LEVELS.contains(riskLevel)) throw new BadRequestException("riskLevel이 올바르지 않습니다.");
        return noStore(service.list(currentUser.userId(), query, riskLevel, sort, cursor, size));
    }

    @GetMapping("/{subjectId}")
    public ResponseEntity<SubjectDetailResponse> detail(@PathVariable Long subjectId) {
        return noStore(service.detail(currentUser.userId(), subjectId));
    }

    @GetMapping("/{subjectId}/snapshot")
    public ResponseEntity<SubjectSnapshotResponse> snapshot(@PathVariable Long subjectId) {
        return noStore(service.snapshot(currentUser.userId(), subjectId));
    }

    @GetMapping("/{subjectId}/power-usage")
    public ResponseEntity<PowerUsageResponse> powerUsage(@PathVariable Long subjectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return noStore(service.powerUsage(currentUser.userId(), subjectId, date));
    }

    @GetMapping("/{subjectId}/analysis-events")
    public ResponseEntity<CursorPage<AnalysisEventResponse>> events(@PathVariable Long subjectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return noStore(service.analysisEvents(currentUser.userId(), subjectId, from, to, cursor, size));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
