package com.internlink.core.application.organization.impl;

import com.internlink.core.application.organization.RosterLecturerAssignmentService;
import com.internlink.core.application.placement.PlacementEligibilityService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.organization.dto.response.BulkRosterLecturerResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RosterLecturerAssignmentServiceImpl implements RosterLecturerAssignmentService {

    private final JpaStudentRosterRepository rosterRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaUserRepository userRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final PlacementEligibilityService eligibilityService;
    private final SecurityGuard securityGuard;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final StudentRosterMapper mapper;

    @Override
    @Transactional
    public StudentRosterResponse assign(UUID rosterId, UUID lecturerId, String reason) {
        StudentRoster roster = loadRoster(rosterId);
        InternshipTerm term = roster.getTerm();
        UUID actorId = requireManager(term);
        User lecturer = loadLecturer(lecturerId, term);
        applyAssignment(roster, lecturer, reason, actorId);
        return mapper.mapToResponse(roster);
    }

    @Override
    @Transactional
    public BulkRosterLecturerResponse assignBulk(UUID termId, List<UUID> rosterIds, UUID lecturerId, String reason) {
        if (rosterIds == null || rosterIds.isEmpty()) {
            throw new BadRequestException("Chưa chọn sinh viên để phân công");
        }
        InternshipTerm term = termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        UUID actorId = requireManager(term);
        User lecturer = loadLecturer(lecturerId, term);

        Set<UUID> wanted = new HashSet<>(rosterIds);
        int updated = 0;
        int unchanged = 0;
        List<String> failures = new ArrayList<>();
        for (StudentRoster roster : rosterRepository.findByTermIdOrderByStudentCodeAsc(termId)) {
            if (!wanted.remove(roster.getId())) continue;
            try {
                if (applyAssignment(roster, lecturer, reason, actorId)) updated++;
                else unchanged++;
            } catch (BadRequestException ex) {
                failures.add(roster.getStudentCode() + ": " + ex.getMessage());
            }
        }
        if (!wanted.isEmpty()) {
            failures.add(wanted.size() + " dòng không thuộc học kỳ này");
        }
        return new BulkRosterLecturerResponse(updated, unchanged, failures);
    }

    @Override
    @Transactional
    public StudentRosterResponse unassign(UUID rosterId) {
        StudentRoster roster = loadRoster(rosterId);
        UUID actorId = requireManager(roster.getTerm());
        if (livePlacement(roster).isPresent()) {
            throw new BadRequestException("Sinh viên đang có lần thực tập, hãy đổi sang GVHD khác thay vì gỡ phân công");
        }
        User previous = roster.getAssignedLecturer();
        if (previous == null && roster.getLecturerEmail() == null && roster.getLecturerName() == null) {
            return mapper.mapToResponse(roster);
        }
        roster.setAssignedLecturer(null);
        roster.setLecturerCode(null);
        roster.setLecturerName(null);
        roster.setLecturerEmail(null);
        rosterRepository.save(roster);

        Map<String, Object> changes = new HashMap<>();
        changes.put("studentCode", roster.getStudentCode());
        changes.put("previousLecturerId", previous != null ? previous.getId() : null);
        auditLogService.logAction(actorId, "UNASSIGN_ROSTER_LECTURER", "StudentRoster", roster.getId(),
                "SUCCESS", changes, null);
        if (previous != null) {
            notificationService.sendNotification(previous.getId(), "LECTURER_UNASSIGNED", "Thay đổi phân công hướng dẫn",
                    "Bạn không còn được phân công hướng dẫn sinh viên " + roster.getFullName(), "/lecturer/supervision");
        }
        return mapper.mapToResponse(roster);
    }

    /** Returns false when the roster (and its live placement) is already on this lecturer. */
    private boolean applyAssignment(StudentRoster roster, User lecturer, String reason, UUID actorId) {
        User previous = roster.getAssignedLecturer();
        Optional<InternshipPlacement> placement = livePlacement(roster);
        boolean placementOnLecturer = placement
                .map(p -> p.getLecturer() != null && p.getLecturer().getId().equals(lecturer.getId()))
                .orElse(true);
        if (previous != null && previous.getId().equals(lecturer.getId()) && placementOnLecturer) {
            return false;
        }

        String cleanReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        if (placement.isPresent()) {
            InternshipPlacement p = placement.get();
            if (p.getStatus() == PlacementStatus.COMPLETED) {
                throw new BadRequestException("Sinh viên đã hoàn thành thực tập, không thể đổi GVHD");
            }
            if (p.getLecturer() != null && !p.getLecturer().getId().equals(lecturer.getId()) && cleanReason == null) {
                throw new BadRequestException("Sinh viên đang thực tập, cần nhập lý do đổi GVHD");
            }
        }

        UUID previousId = previous != null ? previous.getId()
                : placement.map(InternshipPlacement::getLecturer).map(User::getId).orElse(null);
        if (lecturer.getEmail() == null || !lecturer.getEmail().equalsIgnoreCase(roster.getLecturerEmail())) {
            // The imported code belonged to the lecturer being replaced.
            roster.setLecturerCode(null);
        }
        roster.setAssignedLecturer(lecturer);
        roster.setLecturerName(lecturer.getFullName());
        roster.setLecturerEmail(lecturer.getEmail());
        rosterRepository.save(roster);
        placement.ifPresent(p -> {
            p.setLecturer(lecturer);
            placementRepository.save(p);
        });

        Map<String, Object> changes = new HashMap<>();
        changes.put("studentCode", roster.getStudentCode());
        changes.put("previousLecturerId", previousId);
        changes.put("lecturerId", lecturer.getId());
        changes.put("placementId", placement.map(InternshipPlacement::getId).orElse(null));
        changes.put("reason", cleanReason);
        auditLogService.logAction(actorId, previousId == null ? "ASSIGN_ROSTER_LECTURER" : "REASSIGN_ROSTER_LECTURER",
                "StudentRoster", roster.getId(), "SUCCESS", changes, null);

        notificationService.sendNotification(lecturer.getId(), "LECTURER_ASSIGNED", "Phân công hướng dẫn thực tập",
                "Bạn được phân công hướng dẫn sinh viên " + roster.getFullName() + " (" + roster.getStudentCode() + ")",
                "/lecturer/supervision");
        if (previousId != null && !previousId.equals(lecturer.getId())) {
            notificationService.sendNotification(previousId, "LECTURER_UNASSIGNED", "Thay đổi phân công hướng dẫn",
                    "Sinh viên " + roster.getFullName() + " đã được chuyển sang GVHD " + lecturer.getFullName()
                            + (cleanReason != null ? ". Lý do: " + cleanReason : ""),
                    "/lecturer/supervision");
        }
        if (roster.getClaimedUser() != null) {
            notificationService.sendNotification(roster.getClaimedUser().getId(), "LECTURER_ASSIGNED", "Giảng viên hướng dẫn",
                    "Giảng viên hướng dẫn của bạn là " + lecturer.getFullName(), "/student/dashboard");
        }
        return true;
    }

    private Optional<InternshipPlacement> livePlacement(StudentRoster roster) {
        if (roster.getClaimedUser() == null) return Optional.empty();
        return placementRepository.findByTermIdAndStudentId(roster.getTerm().getId(), roster.getClaimedUser().getId())
                .stream()
                .filter(p -> p.getStatus() != PlacementStatus.TERMINATED && p.getStatus() != PlacementStatus.TRANSFERRED)
                .findFirst();
    }

    private StudentRoster loadRoster(UUID rosterId) {
        return rosterRepository.findById(rosterId)
                .orElseThrow(() -> new ResourceNotFoundException("StudentRoster", "id", rosterId));
    }

    private UUID requireManager(InternshipTerm term) {
        TermGuard.requireNotClosed(term);
        User actor = userRepository.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(actor, term.getDepartment().getId()));
        return actor.getId();
    }

    private User loadLecturer(UUID lecturerId, InternshipTerm term) {
        if (lecturerId == null) {
            throw new BadRequestException("Cần chọn giảng viên hướng dẫn");
        }
        User lecturer = userRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        eligibilityService.requireActiveLecturer(lecturer, term.getDepartment().getId());
        return lecturer;
    }
}
