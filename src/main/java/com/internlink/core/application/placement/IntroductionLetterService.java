package com.internlink.core.application.placement;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.IntroductionLetterRequest;
import com.internlink.core.infrastructure.persistence.jpa.JpaIntroductionLetterRequestRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.placement.dto.request.CreateIntroductionLetterRequest;
import com.internlink.core.presentation.placement.dto.request.ReviewIntroductionLetterRequest;
import com.internlink.core.presentation.placement.dto.response.IntroductionLetterResponse;
import com.internlink.core.shared.enums.IntroductionLetterStatus;
import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class IntroductionLetterService {
    private final JpaIntroductionLetterRequestRepository letterRequests;
    private final JpaInternshipTermRepository terms;
    private final JpaStudentRosterRepository rosters;
    private final JpaUserRepository users;
    private final SecurityGuard security;
    private final NotificationService notifications;
    private final IntroductionLetterResponseMapper responseMapper = new IntroductionLetterResponseMapper();
    @Transactional
    public IntroductionLetterResponse create(CreateIntroductionLetterRequest request) {
        User student = actor();
        ResourceAuthorization.require(student.getRole() == UserRole.STUDENT);
        var term = terms.findById(request.termId())
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", request.termId()));
        if (term.getStatus() != TermStatus.REGISTRATION_OPEN && term.getStatus() != TermStatus.APPLICATION_OPEN) {
            throw new BadRequestException("Kỳ thực tập chưa mở hoặc đã đóng đăng ký tiếp nhận");
        }
        var roster = rosters.findByTermIdAndClaimedUserId(term.getId(), student.getId())
                .orElseThrow(() -> new BadRequestException("Sinh viên chưa được xác nhận trong danh sách kỳ thực tập"));
        List<IntroductionLetterStatus> activeStatuses = List.of(
                IntroductionLetterStatus.PENDING,
                IntroductionLetterStatus.APPROVED,
                IntroductionLetterStatus.ISSUED
        );
        var existingActive = letterRequests.findActiveRequestsByStudentAndTerm(student.getId(), term.getId(), activeStatuses);
        if (!existingActive.isEmpty()) {
            throw new BadRequestException("Mỗi sinh viên chỉ được cấp 1 giấy giới thiệu trong kỳ thực tập. Bạn đã có yêu cầu đang xử lý hoặc đã được cấp.");
        }
        IntroductionLetterRequest entity = IntroductionLetterRequest.builder()
                .student(student)
                .term(term)
                .targetCompanyName(request.targetCompanyName().trim())
                .targetPosition(request.targetPosition() != null ? request.targetPosition().trim() : null)
                .reason(request.reason() != null ? request.reason().trim() : null)
                .status(IntroductionLetterStatus.PENDING)
                .build();
        IntroductionLetterRequest saved = letterRequests.save(entity);
        return responseMapper.fromRequest(saved, roster.getStudentCode());
    }
    @Transactional
    public IntroductionLetterResponse review(UUID id, ReviewIntroductionLetterRequest request) {
        User office = actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(office));
        IntroductionLetterRequest entity = letterRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("IntroductionLetterRequest", "id", id));
                IntroductionLetterPolicy.requireTransition(entity.getStatus(), request.status());
        entity.setStatus(request.status());
        entity.setIssueNote(request.issueNote());
        entity.setProcessedBy(office);
        entity.setProcessedAt(OffsetDateTime.now());
        if (request.status() == IntroductionLetterStatus.APPROVED) {
            String code = IntroductionLetterPolicy.issuedCode(entity, request.issuedCode());
            entity.setIssuedCode(code);
        } else if (request.status() == IntroductionLetterStatus.ISSUED && entity.getReceivedAt() == null) {
            entity.setReceivedAt(OffsetDateTime.now());
        }
        IntroductionLetterRequest saved = letterRequests.save(entity);
        var roster = rosters.findByTermIdAndClaimedUserId(entity.getTerm().getId(), entity.getStudent().getId());
        String studentCode = roster.map(StudentRoster::getStudentCode).orElse(null);
        if (request.status() == IntroductionLetterStatus.APPROVED) {
            notifications.sendNotification(
                    entity.getStudent().getId(),
                    "INFO",
                    "Giấy giới thiệu thực tập sẵn sàng",
                    "Giấy giới thiệu của bạn (" + entity.getTargetCompanyName() + ") đã được duyệt với mã "
                            + entity.getIssuedCode() + ". Vui lòng đến sảnh Văn phòng Trường CNTT-TT để nhận bản in.",
                    "/student/acceptance-form"
            );
        } else if (request.status() == IntroductionLetterStatus.REJECTED) {
            notifications.sendNotification(
                    entity.getStudent().getId(),
                    "WARNING",
                    "Yêu cầu cấp giấy giới thiệu bị từ chối",
                    "Lý do: " + (request.issueNote() != null ? request.issueNote() : "Không đạt yêu cầu"),
                    "/student/acceptance-form"
            );
        }
        return responseMapper.fromRequest(saved, studentCode);
    }
    @Transactional
    public IntroductionLetterResponse confirmReceived(UUID id) {
        User office = actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(office));
        IntroductionLetterRequest entity = letterRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("IntroductionLetterRequest", "id", id));
                if (entity.getStatus() != IntroductionLetterStatus.APPROVED) {
            throw new BadRequestException("Chỉ giấy giới thiệu đã được duyệt và chưa phát hành mới có thể xác nhận đã nhận");
        }
        entity.setStatus(IntroductionLetterStatus.ISSUED);
        entity.setReceivedAt(OffsetDateTime.now());
        IntroductionLetterRequest saved = letterRequests.save(entity);
        var roster = rosters.findByTermIdAndClaimedUserId(entity.getTerm().getId(), entity.getStudent().getId());
        roster.ifPresent(r -> {
            r.setLetterReceived(true);
            r.setLetterReceivedAt(saved.getReceivedAt());
            rosters.save(r);
        });
        String studentCode = roster.map(StudentRoster::getStudentCode).orElse(null);
        return responseMapper.fromRequest(saved, studentCode);
    }
    @Transactional
    public IntroductionLetterResponse toggleRosterLetterReceived(UUID rosterId) {
        User office = actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(office));
        var roster = rosters.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentRoster", "id", rosterId));
        boolean newState = !roster.isLetterReceived();
        roster.setLetterReceived(newState);
        roster.setLetterReceivedAt(newState ? OffsetDateTime.now() : null);
        StudentRoster savedRoster = rosters.save(roster);
        if (savedRoster.getClaimedUser() != null) {
            letterRequests.findFirstByStudentIdAndTermIdOrderByCreatedAtDesc(savedRoster.getClaimedUser().getId(), savedRoster.getTerm().getId())
                    .ifPresent(req -> {
                        if (newState) {
                            req.setStatus(IntroductionLetterStatus.ISSUED);
                            req.setReceivedAt(savedRoster.getLetterReceivedAt());
                        } else if (req.getStatus() == IntroductionLetterStatus.ISSUED) {
                            req.setStatus(IntroductionLetterStatus.APPROVED);
                            req.setReceivedAt(null);
                        }
                        letterRequests.save(req);
                    });
        }
        return responseMapper.fromRoster(savedRoster);
    }
    @Transactional
    public List<IntroductionLetterResponse> batchConfirmRosterLetterReceived(List<UUID> rosterIds) {
        User office = actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(office));
        OffsetDateTime now = OffsetDateTime.now();
        return rosterIds.stream().map(id -> {
            var rosterOpt = rosters.findById(id);
            if (rosterOpt.isEmpty()) return null;
            var roster = rosterOpt.get();
            roster.setLetterReceived(true);
            roster.setLetterReceivedAt(now);
            StudentRoster saved = rosters.save(roster);
            if (saved.getClaimedUser() != null) {
                letterRequests.findFirstByStudentIdAndTermIdOrderByCreatedAtDesc(saved.getClaimedUser().getId(), saved.getTerm().getId())
                        .ifPresent(req -> {
                            req.setStatus(IntroductionLetterStatus.ISSUED);
                            req.setReceivedAt(now);
                            letterRequests.save(req);
                        });
            }
            return responseMapper.fromRoster(saved);
        }).filter(Objects::nonNull).toList();
    }
    @Transactional
    public IntroductionLetterResponse cancel(UUID id) {
        User student = actor();
        IntroductionLetterRequest entity = letterRequests.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("IntroductionLetterRequest", "id", id));
        ResourceAuthorization.require(student.getId().equals(entity.getStudent().getId()));
        if (entity.getStatus() != IntroductionLetterStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy yêu cầu khi đang ở trạng thái chờ duyệt (PENDING)");
        }
        entity.setStatus(IntroductionLetterStatus.CANCELLED);
        IntroductionLetterRequest saved = letterRequests.save(entity);
        var roster = rosters.findByTermIdAndClaimedUserId(entity.getTerm().getId(), entity.getStudent().getId());
        String studentCode = roster.map(StudentRoster::getStudentCode).orElse(null);
        return responseMapper.fromRequest(saved, studentCode);
    }
    @Transactional(readOnly = true)
    public List<IntroductionLetterResponse> mine() {
        User student = actor();
        List<IntroductionLetterRequest> requests = letterRequests.findByStudentIdOrderByCreatedAtDesc(student.getId());
        if (!requests.isEmpty()) {
            return requests.stream()
                    .map(item -> {
                        var roster = rosters.findByTermIdAndClaimedUserId(item.getTerm().getId(), student.getId());
                        return responseMapper.fromRequest(item, roster.map(StudentRoster::getStudentCode).orElse(null));
                    })
                    .toList();
        }
        return rosters.findFirstByClaimedUserIdOrderByCreatedAtDesc(student.getId())
                .map(r -> List.of(responseMapper.fromRoster(r)))
                .orElse(List.of());
    }
    @Transactional(readOnly = true)
    public List<IntroductionLetterResponse> byTerm(UUID termId) {
        User user = actor();
        var term = terms.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        UUID departmentId = term.getDepartment().getId();
        // VP Trường xử lý toàn trường; Khoa và GV chỉ theo dõi danh sách của khoa mình.
        ResourceAuthorization.require(
                ResourceAuthorization.actsForSchool(user)
                        || ResourceAuthorization.managesDepartment(user, departmentId)
                        || (user.getRole() == UserRole.LECTURER
                                && user.getDepartment() != null
                                && user.getDepartment().getId().equals(departmentId)));
        List<StudentRoster> termRosters = rosters.findByTermIdOrderByStudentCodeAsc(termId);
        if (!termRosters.isEmpty()) {
            Map<UUID, IntroductionLetterRequest> requestByStudentId = letterRequests.findByTermIdOrderByCreatedAtDesc(termId)
                    .stream()
                    .filter(r -> r.getStudent() != null)
                    .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r, (a, b) -> a));
            return termRosters.stream()
                    .map(roster -> {
                        IntroductionLetterRequest req = roster.getClaimedUser() != null
                                ? requestByStudentId.get(roster.getClaimedUser().getId())
                                : null;
                        return responseMapper.fromRoster(roster, req);
                    })
                    .toList();
        }
        return letterRequests.findByTermIdOrderByCreatedAtDesc(termId)
                .stream()
                .map(item -> {
                    var roster = rosters.findByTermIdAndClaimedUserId(item.getTerm().getId(), item.getStudent().getId());
                    return responseMapper.fromRequest(item, roster.map(StudentRoster::getStudentCode).orElse(null));
                })
                .toList();
    }
    private User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
