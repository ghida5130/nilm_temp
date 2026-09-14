package com.nilm.monitoring.subject.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nilm.monitoring.common.BadRequestException;
import com.nilm.monitoring.common.ConflictException;
import com.nilm.monitoring.common.ResourceNotFoundException;
import com.nilm.monitoring.common.ServiceUnavailableException;
import com.nilm.monitoring.common.dto.CursorPage;
import com.nilm.monitoring.domain.CareSubject;
import com.nilm.monitoring.domain.StaffProfile;
import com.nilm.monitoring.domain.StaffSubjectAssignment;
import com.nilm.monitoring.domain.SubjectStatus;
import com.nilm.monitoring.policy.repository.RiskPolicyRepository;
import com.nilm.monitoring.outbox.service.OutboxWriter;
import com.nilm.monitoring.staff.service.StaffAccountService;
import com.nilm.monitoring.subject.dto.AssignmentResponse;
import com.nilm.monitoring.subject.dto.CreateAssignmentRequest;
import com.nilm.monitoring.subject.dto.SubjectCandidateResponse;
import com.nilm.monitoring.subject.dto.SubjectCandidateSearchRequest;
import com.nilm.monitoring.subject.dto.UpdateAssignmentRequest;
import com.nilm.monitoring.subject.dto.UpdateAssignmentResponse;
import com.nilm.monitoring.subject.repository.AssignmentRepository;
import com.nilm.monitoring.subject.repository.CareSubjectRepository;
import com.nilm.monitoring.subject.repository.StaffRegionAccessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<SubjectStatus> CANDIDATE_STATUSES = List.of(
            SubjectStatus.ACTIVE, SubjectStatus.PENDING, SubjectStatus.PAUSED);
    private final AssignmentRepository assignments;
    private final CareSubjectRepository subjects;
    private final StaffRegionAccessRepository regions;
    private final RiskPolicyRepository policies;
    private final StaffAccountService staffAccounts;
    private final PiiCodec pii;
    private final Clock clock;
    private final OutboxWriter outbox;
    private final ObjectMapper mapper;
    private final CandidateSearchRateLimiter candidateRateLimiter;

    public AssignmentService(AssignmentRepository assignments, CareSubjectRepository subjects,
            StaffRegionAccessRepository regions, RiskPolicyRepository policies,
            StaffAccountService staffAccounts, PiiCodec pii, Clock clock,
            OutboxWriter outbox, ObjectMapper mapper, CandidateSearchRateLimiter candidateRateLimiter) {
        this.assignments = assignments; this.subjects = subjects; this.regions = regions;
        this.policies = policies; this.staffAccounts = staffAccounts; this.pii = pii; this.clock = clock;
        this.outbox = outbox; this.mapper = mapper;
        this.candidateRateLimiter = candidateRateLimiter;
    }

    @Transactional(readOnly = true)
    public CursorPage<SubjectCandidateResponse> candidates(String authSub, SubjectCandidateSearchRequest request) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        candidateRateLimiter.check(authSub);
        boolean hasName = request.name() != null && !pii.normalize(request.name()).isBlank();
        boolean hasNumber = request.subjectNumber() != null && !request.subjectNumber().isBlank();
        if (hasName == hasNumber || (!hasName && request.birthDate() != null)) {
            throw new BadRequestException("name 또는 subjectNumber 중 하나만 입력해야 합니다.");
        }
        List<String> allowedRegions = regions.findRegionCodes(staff.getId());
        List<CareSubject> found;
        if (allowedRegions.isEmpty()) {
            found = List.of();
        } else if (hasNumber) {
            found = subjects.findBySubjectNumber(request.subjectNumber().trim())
                    .filter(s -> allowedRegions.contains(s.getRegionCode()) && CANDIDATE_STATUSES.contains(s.getStatus()))
                    .map(List::of).orElse(List.of());
        } else {
            String normalized = pii.normalize(request.name());
            if (normalized.length() > 100) throw new BadRequestException("name은 1~100자여야 합니다.");
            found = subjects.findByNameBlindIndexAndRegionCodeInAndStatusIn(
                    pii.blindIndex(normalized), allowedRegions, CANDIDATE_STATUSES);
            if (request.birthDate() != null) found = found.stream()
                    .filter(s -> s.getBirthDate().equals(request.birthDate())).toList();
        }
        List<SubjectCandidateResponse> values = found.stream()
                .sorted(Comparator.comparing(CareSubject::getId))
                .map(s -> new SubjectCandidateResponse(s.getId().toString(), pii.decrypt(s.getName()),
                        age(s.getBirthDate()), addressSummary(pii.decrypt(s.getAddress())), s.getStatus().name(),
                        assignments.existsByStaffIdAndSubjectIdAndUnassignedAtIsNull(staff.getId(), s.getId())))
                .toList();
        int size = request.size() == null ? 20 : request.size();
        String fingerprint = hasNumber ? "number|" + request.subjectNumber().trim()
                : "name|" + pii.normalize(request.name()) + "|" + request.birthDate();
        int offset = CursorCodec.decode(request.cursor(), fingerprint);
        if (offset > values.size()) throw new BadRequestException("cursor가 만료되었습니다.");
        int end = Math.min(offset + size, values.size());
        boolean hasNext = end < values.size();
        return new CursorPage<>(values.subList(offset, end),
                hasNext ? CursorCodec.encode(end, fingerprint) : null, hasNext, Instant.now(clock));
    }

    @Transactional
    public AssignmentResponse create(String authSub, CreateAssignmentRequest request) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        long subjectId = parseId(request.subjectId(), "subjectId");
        long policyId = parseId(request.riskPolicyId(), "riskPolicyId");
        CareSubject subject = subjects.findByIdForUpdate(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("대상자를 찾을 수 없습니다."));
        if (!regions.findRegionCodes(staff.getId()).contains(subject.getRegionCode())
                || !CANDIDATE_STATUSES.contains(subject.getStatus())) {
            throw new ResourceNotFoundException("대상자를 찾을 수 없습니다.");
        }
        if (!policies.existsById(policyId)) throw new ResourceNotFoundException("위험 정책을 찾을 수 없습니다.");
        if (assignments.existsByStaffIdAndSubjectIdAndUnassignedAtIsNull(staff.getId(), subjectId)) {
            throw new ConflictException("이미 배정된 대상자입니다.");
        }
        if (subject.getRiskPolicyId() == null) {
            throw new ServiceUnavailableException("분석 서비스의 정책 적용 확인이 필요합니다.");
        }
        if (!subject.getRiskPolicyId().equals(policyId)) {
            throw new ConflictException("현재 대상자에게 적용된 정책과 다른 정책은 선택할 수 없습니다.");
        }
        Instant now = Instant.now(clock);
        StaffSubjectAssignment saved = assignments.saveAndFlush(StaffSubjectAssignment.assign(
                staff.getId(), subjectId, staff.getId(), request.memo() == null ? "" : request.memo(), now));
        outbox.writePersonal(authSub, "assignment-updated", mapper.createObjectNode()
                .put("subjectId", subjectId).put("assignmentId", saved.getId())
                .put("changeType", "ASSIGNED").put("changedAt", now.toString()));
        return response(saved, policyId);
    }

    @Transactional
    public UpdateAssignmentResponse update(String authSub, Long assignmentId, UpdateAssignmentRequest request) {
        StaffProfile staff = staffAccounts.requireActive(authSub);
        StaffSubjectAssignment assignment = assignments.findOwnedForUpdate(assignmentId, staff.getId())
                .orElseThrow(() -> new ResourceNotFoundException("배정을 찾을 수 없습니다."));
        if (!assignment.isActive()) throw new ConflictException("이미 해제된 배정입니다.");
        StaffAccountService.requireRevision(request.expectedRevision(), assignment.getRevision());
        assignment.updateMemo(request.memo(), Instant.now(clock));
        assignments.flush();
        return new UpdateAssignmentResponse(assignment.getId().toString(),
                nullToEmpty(assignment.getMemo()), Long.toString(assignment.getRevision() + 1),
                assignment.getUpdatedAt());
    }

    private AssignmentResponse response(StaffSubjectAssignment assignment, Long policyId) {
        return new AssignmentResponse(assignment.getId().toString(), assignment.getSubjectId().toString(),
                policyId == null ? null : policyId.toString(),
                Long.toString(assignment.getRevision() + 1), assignment.getAssignedAt());
    }
    private static long parseId(String value, String name) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new Exception(); return id; }
        catch (Exception e) { throw new BadRequestException(name + "가 올바르지 않습니다."); }
    }
    private int age(LocalDate birthDate) { return Period.between(birthDate, LocalDate.now(clock.withZone(SEOUL))).getYears(); }
    private static String addressSummary(String address) {
        String[] parts = address.trim().split("\\s+");
        return String.join(" ", java.util.Arrays.copyOf(parts, Math.min(parts.length, 3)));
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
