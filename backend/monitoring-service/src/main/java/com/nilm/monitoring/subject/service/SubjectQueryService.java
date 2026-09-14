package com.nilm.monitoring.subject.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nilm.monitoring.common.BadRequestException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.common.dto.CursorPage;
import com.nilm.monitoring.domain.AnalysisEvent;
import com.nilm.monitoring.domain.CareSubject;
import com.nilm.monitoring.domain.Incident;
import com.nilm.monitoring.domain.PowerUsageHourly;
import com.nilm.monitoring.domain.RiskLevel;
import com.nilm.monitoring.domain.RiskPolicy;
import com.nilm.monitoring.domain.StaffProfile;
import com.nilm.monitoring.domain.StaffSubjectAssignment;
import com.nilm.monitoring.incident.repository.AnalysisEventRepository;
import com.nilm.monitoring.incident.repository.IncidentRepository;
import com.nilm.monitoring.policy.repository.RiskPolicyRepository;
import com.nilm.monitoring.snapshot.dto.MonitoringSnapshot;
import com.nilm.monitoring.snapshot.repository.SnapshotRepository;
import com.nilm.monitoring.staff.service.StaffAccountService;
import com.nilm.monitoring.subject.dto.AnalysisEventResponse;
import com.nilm.monitoring.subject.dto.DashboardSummaryResponse;
import com.nilm.monitoring.subject.dto.PowerUsageResponse;
import com.nilm.monitoring.subject.dto.SubjectCardResponse;
import com.nilm.monitoring.subject.dto.SubjectDetailResponse;
import com.nilm.monitoring.subject.dto.SubjectSnapshotResponse;
import com.nilm.monitoring.subject.repository.AssignmentRepository;
import com.nilm.monitoring.subject.repository.CareSubjectRepository;
import com.nilm.monitoring.subject.repository.PowerUsageHourlyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubjectQueryService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Set<String> ALLOWED_REASON_FIELDS = Set.of(
            "expected_until", "appliance_type", "expected", "observed");
    private final CareSubjectRepository subjects;
    private final AssignmentRepository assignments;
    private final IncidentRepository incidents;
    private final AnalysisEventRepository events;
    private final PowerUsageHourlyRepository powerUsage;
    private final RiskPolicyRepository policies;
    private final SnapshotRepository snapshots;
    private final StaffAccountService staffAccounts;
    private final PiiCodec pii;
    private final ObjectMapper mapper;
    private final Clock clock;

    public SubjectQueryService(CareSubjectRepository subjects, AssignmentRepository assignments,
            IncidentRepository incidents, AnalysisEventRepository events,
            PowerUsageHourlyRepository powerUsage, RiskPolicyRepository policies,
            SnapshotRepository snapshots, StaffAccountService staffAccounts,
            PiiCodec pii, ObjectMapper mapper, Clock clock) {
        this.subjects = subjects; this.assignments = assignments; this.incidents = incidents;
        this.events = events; this.powerUsage = powerUsage; this.policies = policies;
        this.snapshots = snapshots; this.staffAccounts = staffAccounts; this.pii = pii;
        this.mapper = mapper; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPage<SubjectCardResponse> list(String authSub, String query, String riskLevel,
                                                String sort, String cursor, int size) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        String normalized = query == null ? null : pii.normalize(query);
        if (normalized != null && (normalized.isBlank() || normalized.length() > 100)) {
            throw new BadRequestException("query는 공백을 제외하고 1~100자여야 합니다.");
        }
        List<SubjectCardResponse> cards = new ArrayList<>();
        for (StaffSubjectAssignment assignment : assignments.findActiveByStaffId(staff.getId())) {
            subjects.findById(assignment.getSubjectId()).filter(this::visibleStatus)
                    .filter(subject -> matchesQuery(subject, normalized))
                    .ifPresent(subject -> cards.add(card(subject, assignment)));
        }
        if (riskLevel != null) cards.removeIf(card -> !card.riskLevel().equals(riskLevel));
        cards.sort(comparator(sort));
        String fingerprint = String.join("|", normalized == null ? "" : normalized,
                riskLevel == null ? "" : riskLevel, sort);
        return page(cards, cursor, size, fingerprint);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse dashboard(String authSub) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        List<SubjectCardResponse> cards = new ArrayList<>();
        for (StaffSubjectAssignment assignment : assignments.findActiveByStaffId(staff.getId())) {
            subjects.findById(assignment.getSubjectId()).filter(this::visibleStatus)
                    .ifPresent(subject -> cards.add(card(subject, assignment)));
        }
        long danger = cards.stream().filter(c -> c.riskLevel().equals("DANGER")).count();
        long warning = cards.stream().filter(c -> c.riskLevel().equals("WARNING")).count();
        long normal = cards.stream().filter(c -> c.riskLevel().equals("NORMAL")).count();
        long unknown = cards.size() - danger - warning - normal;
        return new DashboardSummaryResponse(cards.size(), danger, warning, normal, unknown,
                Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public SubjectDetailResponse detail(String authSub, Long subjectId) {
        Owned owned = requireOwned(authSub, subjectId);
        CareSubject subject = owned.subject();
        RiskPolicy policy = subject.getRiskPolicyId() == null ? null
                : policies.findById(subject.getRiskPolicyId()).orElse(null);
        SubjectDetailResponse.RiskPolicySummary policyResponse = policy == null ? null
                : new SubjectDetailResponse.RiskPolicySummary(policy.getId().toString(),
                    policy.getVersion(), policy.getPolicyName());
        return new SubjectDetailResponse(subject.getId().toString(), subject.getHouseholdId(),
                subject.getSubjectNumber(), pii.decrypt(subject.getName()), age(subject.getBirthDate()),
                pii.decrypt(subject.getPhone()), pii.decrypt(subject.getAddress()), subject.getStatus().name(),
                incidents.countBySubjectId(subjectId), incidents.countOpenBySubjectId(subjectId),
                new SubjectDetailResponse.Assignment(owned.assignment().getId().toString(),
                        nullToEmpty(owned.assignment().getMemo()),
                        Long.toString(owned.assignment().getRevision() + 1)),
                policyResponse, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public SubjectSnapshotResponse snapshot(String authSub, Long subjectId) {
        Owned owned = requireOwned(authSub, subjectId);
        CareSubject subject = owned.subject();
        SubjectSnapshotResponse.LatestDetection latest = events
                .findFirstBySubjectIdOrderByOccurredAtDescEventIdDesc(subjectId)
                .map(this::latest).orElse(null);
        Instant now = Instant.now(clock);
        Optional<MonitoringSnapshot> snapshot = snapshots.find(subject.getHouseholdId());
        if (snapshot.isEmpty()) {
            return new SubjectSnapshotResponse(subjectId.toString(), subject.getHouseholdId(), "UNKNOWN",
                    null, null, null, null, null, null, null, null, List.of(), latest, now);
        }
        MonitoringSnapshot value = snapshot.get();
        return new SubjectSnapshotResponse(subjectId.toString(), subject.getHouseholdId(), "AVAILABLE",
                value.revision().toString(), value.observedAt(), value.expiresAt(), value.riskScore(),
                value.riskLevel() == null ? null : value.riskLevel().name(), value.dataStatus(),
                value.activePowerW(), value.lastActivityAt(), value.appliances(), latest, now);
    }

    @Transactional(readOnly = true)
    public PowerUsageResponse powerUsage(String authSub, Long subjectId, LocalDate date) {
        Owned owned = requireOwned(authSub, subjectId);
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        if (date.isAfter(today)) throw new BadRequestException("미래 날짜는 조회할 수 없습니다.");
        Instant from = date.atStartOfDay(SEOUL).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(SEOUL).toInstant();
        Map<Instant, PowerUsageHourly> values = new HashMap<>();
        powerUsage.findRange(owned.subject().getHouseholdId(), from, to)
                .forEach(value -> values.put(value.getId().getHourStart(), value));
        List<PowerUsageResponse.Point> points = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (int hour = 0; hour < 24; hour++) {
            Instant start = date.atTime(hour, 0).atZone(SEOUL).toInstant();
            PowerUsageHourly value = values.get(start);
            if (!start.isBefore(now)) {
                points.add(emptyPoint(start, "NOT_YET"));
            } else if (value == null || !value.hasData()) {
                points.add(emptyPoint(start, "MISSING"));
            } else {
                String status = value.getCoverageRatio().compareTo(BigDecimal.ONE) == 0 ? "COMPLETE" : "PARTIAL";
                points.add(new PowerUsageResponse.Point(start, value.getEnergyWh(), value.getAvgActivePowerW(),
                        value.getMaxActivePowerW(), value.getSampleCount(), value.getCoverageRatio(), status,
                        value.getAggregationVersion()));
            }
        }
        return new PowerUsageResponse(subjectId.toString(), date, SEOUL.getId(), "PT1H", points, now);
    }

    @Transactional(readOnly = true)
    public CursorPage<AnalysisEventResponse> analysisEvents(String authSub, Long subjectId,
            LocalDate fromDate, LocalDate toDate, String cursor, int size) {
        requireOwned(authSub, subjectId);
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        if ((fromDate == null) != (toDate == null)) throw new BadRequestException("from과 to는 함께 전달해야 합니다.");
        LocalDate from = fromDate == null ? today.minusDays(6) : fromDate;
        LocalDate to = toDate == null ? today : toDate;
        if (from.isAfter(to) || from.plusDays(89).isBefore(to)) {
            throw new BadRequestException("조회 기간은 최대 90일입니다.");
        }
        String fingerprint = subjectId + "|" + from + "|" + to;
        int offset = CursorCodec.decode(cursor, fingerprint);
        List<AnalysisEvent> fetched = events.findSubjectEvents(subjectId,
                from.atStartOfDay(SEOUL).toInstant(), to.plusDays(1).atStartOfDay(SEOUL).toInstant(),
                PageRequest.of(0, offset + size + 1));
        List<AnalysisEvent> found = fetched.stream().skip(offset).toList();
        boolean hasNext = found.size() > size;
        List<AnalysisEventResponse> items = found.stream().limit(size).map(this::eventResponse).toList();
        String next = hasNext ? CursorCodec.encode(offset + size, fingerprint) : null;
        return new CursorPage<>(items, next, hasNext, Instant.now(clock));
    }

    private SubjectCardResponse card(CareSubject subject, StaffSubjectAssignment assignment) {
        Optional<MonitoringSnapshot> snapshot = snapshots.find(subject.getHouseholdId());
        String level = snapshot.map(MonitoringSnapshot::riskLevel).map(Enum::name).orElse("UNKNOWN");
        Integer score = snapshot.map(MonitoringSnapshot::riskScore).orElse(null);
        if (subject.getStatus().name().equals("PENDING") || subject.getStatus().name().equals("PAUSED")
                || score == null || snapshot.map(MonitoringSnapshot::riskLevel).orElse(null) == null) {
            level = "UNKNOWN";
        }
        return new SubjectCardResponse(subject.getId().toString(), assignment.getId().toString(),
                subject.getHouseholdId(), pii.decrypt(subject.getName()), age(subject.getBirthDate()),
                addressSummary(pii.decrypt(subject.getAddress())), subject.getStatus().name(), score, level,
                snapshot.isPresent() ? "AVAILABLE" : "UNKNOWN",
                snapshot.map(MonitoringSnapshot::observedAt).orElse(null),
                snapshot.map(MonitoringSnapshot::lastActivityAt).orElse(null),
                incidents.countOpenBySubjectId(subject.getId()));
    }

    private Owned requireOwned(String authSub, Long subjectId) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        StaffSubjectAssignment assignment = assignments.findActiveByStaffId(staff.getId()).stream()
                .filter(value -> value.getSubjectId().equals(subjectId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("대상자를 찾을 수 없습니다."));
        CareSubject subject = subjects.findById(subjectId).filter(this::visibleStatus)
                .orElseThrow(() -> new ResourceNotFoundException("대상자를 찾을 수 없습니다."));
        return new Owned(subject, assignment);
    }

    private boolean visibleStatus(CareSubject subject) {
        return !subject.getStatus().name().equals("ENDED") && !subject.getStatus().name().equals("DECEASED");
    }

    private boolean matchesQuery(CareSubject subject, String normalized) {
        return normalized == null
                || pii.normalize(pii.decrypt(subject.getName())).equals(normalized)
                || pii.normalize(subject.getSubjectNumber()).equals(normalized);
    }

    private Comparator<SubjectCardResponse> comparator(String sort) {
        Comparator<SubjectCardResponse> byId = Comparator.comparing(c -> Long.valueOf(c.subjectId()));
        return switch (sort) {
            case "NAME_ASC" -> Comparator.comparing(SubjectCardResponse::name).thenComparing(byId);
            case "LAST_ACTIVITY_ASC" -> Comparator.comparing(SubjectCardResponse::lastActivityAt,
                    Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byId);
            case "RISK_DESC" -> Comparator.comparingInt((SubjectCardResponse c) -> riskOrder(c.riskLevel()))
                    .thenComparing(SubjectCardResponse::riskScore,
                            Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(byId);
            default -> throw new BadRequestException("지원하지 않는 정렬입니다.");
        };
    }

    private static int riskOrder(String level) {
        return switch (level) { case "DANGER" -> 0; case "WARNING" -> 1; case "NORMAL" -> 2; default -> 3; };
    }

    private <T> CursorPage<T> page(List<T> values, String cursor, int size, String fingerprint) {
        int offset = CursorCodec.decode(cursor, fingerprint);
        if (offset > values.size()) throw new BadRequestException("cursor가 만료되었습니다.");
        int end = Math.min(offset + size, values.size());
        boolean next = end < values.size();
        return new CursorPage<>(List.copyOf(values.subList(offset, end)),
                next ? CursorCodec.encode(end, fingerprint) : null, next, Instant.now(clock));
    }

    private SubjectSnapshotResponse.LatestDetection latest(AnalysisEvent event) {
        return new SubjectSnapshotResponse.LatestDetection(event.getEventId(), event.getEventType(),
                event.getOccurredAt(), description(event.getEventType()));
    }

    private AnalysisEventResponse eventResponse(AnalysisEvent event) {
        UUID incidentId = incidents.findBySourceEventId(event.getEventId()).map(Incident::getIncidentId).orElse(null);
        return new AnalysisEventResponse(event.getEventId(), incidentId, event.getOccurredAt(),
                event.getEventType(), event.getScore(), event.getRiskLevel() == null ? null : event.getRiskLevel().name(),
                event.getApplianceType(), description(event.getEventType()), allowedReason(event.getReason()));
    }

    private JsonNode allowedReason(JsonNode reason) {
        ObjectNode allowed = mapper.createObjectNode();
        ALLOWED_REASON_FIELDS.forEach(name -> { if (reason.has(name)) allowed.set(name, reason.get(name)); });
        return allowed;
    }

    private static String description(String type) {
        return switch (type) {
            case "ROUTINE_MISSED" -> "예상 시각까지 일상 활동이 감지되지 않았습니다.";
            default -> "분석 서비스에서 이상 징후를 감지했습니다.";
        };
    }

    private static PowerUsageResponse.Point emptyPoint(Instant start, String status) {
        return new PowerUsageResponse.Point(start, null, null, null, null, null, status, null);
    }
    private int age(LocalDate birthDate) { return Period.between(birthDate, LocalDate.now(clock.withZone(SEOUL))).getYears(); }
    private static String addressSummary(String address) {
        String[] parts = address.trim().split("\\s+");
        return String.join(" ", java.util.Arrays.copyOf(parts, Math.min(parts.length, 3)));
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private record Owned(CareSubject subject, StaffSubjectAssignment assignment) {}
}
