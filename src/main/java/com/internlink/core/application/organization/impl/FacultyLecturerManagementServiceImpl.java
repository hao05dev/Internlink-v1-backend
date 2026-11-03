package com.internlink.core.application.organization.impl;

import com.internlink.core.application.auth.AccountActivationTokenService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.organization.FacultyLecturerManagementService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.FacultyLecturerQuota;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaFacultyLecturerQuotaRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.organization.dto.request.CreateLecturerRequest;
import com.internlink.core.presentation.organization.dto.request.LecturerImportItem;
import com.internlink.core.presentation.organization.dto.response.BatchLecturerImportResponse;
import com.internlink.core.presentation.organization.dto.response.FacultyLecturerResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.util.SecurePasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacultyLecturerManagementServiceImpl implements FacultyLecturerManagementService {

    private final JpaUserRepository userRepository;
    private final JpaDepartmentRepository departmentRepository;
    private final JpaInternshipTermRepository termRepository;
    private final JpaFacultyLecturerQuotaRepository quotaRepository;
    private final JpaStudentRosterRepository rosterRepository;
    private final PasswordEncoder passwordEncoder;
    private final LecturerProvisionEmailNotifier emailNotifier;
    private final AuditLogService auditLogService;
    private final AccountActivationTokenService activationTokens;

    @Override
    @Transactional(readOnly = true)
    public List<FacultyLecturerResponse> getLecturers(UUID departmentId, UUID termId) {
        List<User> lecturers = userRepository.findByDepartmentIdAndRole(departmentId, UserRole.LECTURER).stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .toList();

        Map<UUID, Integer> quotaMap = new HashMap<>();
        Map<UUID, Long> assignmentCountMap = new HashMap<>();

        if (termId != null) {
            quotaRepository.findByTermId(termId).forEach(q -> quotaMap.put(q.getLecturer().getId(), q.getMaxStudents()));
            // The roster is the source of truth for supervision; placements keep the same lecturer.
            for (Object[] row : rosterRepository.countByAssignedLecturerInTerm(termId)) {
                assignmentCountMap.put((UUID) row[0], (Long) row[1]);
            }
        }

        return lecturers.stream().map(lecturer -> FacultyLecturerResponse.builder()
                .id(lecturer.getId())
                .fullName(lecturer.getFullName())
                .email(lecturer.getEmail())
                .phoneNumber(lecturer.getPhoneNumber())
                .departmentName(lecturer.getDepartment() != null ? lecturer.getDepartment().getName() : "")
                .title("Giảng viên")
                .maxStudents(quotaMap.getOrDefault(lecturer.getId(), 15))
                .assignedCount(assignmentCountMap.getOrDefault(lecturer.getId(), 0L).intValue())
                .build()).toList();
    }

    @Override
    @Transactional
    public FacultyLecturerResponse createLecturer(UUID departmentId, UUID termId, CreateLecturerRequest req, UUID actorId) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", departmentId));

        InternshipTerm term = termId != null ? termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId)) : null;
        if (term != null && !term.getDepartment().getId().equals(departmentId)) {
            throw new com.internlink.core.shared.exception.BadRequestException("Học kỳ không thuộc khoa");
        }
        String email = req.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);
        boolean isNew = false;
        if (user == null) {
            String bootstrapSecret = SecurePasswordGenerator.generateRandomPassword(32);
            user = User.builder()
                    .email(email)
                    .fullName(req.getFullName().trim())
                    .phoneNumber(req.getPhoneNumber() != null ? req.getPhoneNumber().trim() : null)
                    .passwordHash(passwordEncoder.encode(bootstrapSecret))
                    .role(UserRole.LECTURER)
                    .department(dept)
                    .mustChangePassword(true)
                    .isActive(true)
                    .build();
            user = userRepository.save(user);
            isNew = true;
            emailNotifier.sendProvisionEmail(
                    email,
                    user.getFullName(),
                    activationTokens.issue(user),
                    activationTokens.ttlHours(),
                    term != null ? term.getTermName() : null);
        } else {
            requireLecturerInDepartment(user, dept);
            if (user.getDepartment() == null) user.setDepartment(dept);
            if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) user.setPhoneNumber(req.getPhoneNumber().trim());
            userRepository.save(user);
        }

        int maxStudents = req.getMaxStudents() != null && req.getMaxStudents() > 0 ? req.getMaxStudents() : 15;
        if (term != null) {
            upsertQuota(user, term, maxStudents);
        }
        int linkedRosters = rosterRepository.linkUnassignedByLecturerEmail(user, email, departmentId);

        auditLogService.logAction(actorId, "CREATE_OR_UPDATE_LECTURER", "User", user.getId(), "SUCCESS",
                Map.of("email", email, "isNew", isNew, "maxStudents", maxStudents, "linkedRosters", linkedRosters), null);

        return FacultyLecturerResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .departmentName(dept.getName())
                .title(req.getTitle() != null ? req.getTitle() : "Giảng viên")
                .maxStudents(maxStudents)
                .assignedCount(term != null ? countAssigned(term.getId(), user.getId()) : 0)
                .build();
    }

    @Override
    @Transactional
    public BatchLecturerImportResponse importLecturers(UUID departmentId, UUID termId, List<LecturerImportItem> items, UUID actorId) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", departmentId));
        InternshipTerm term = termId != null ? termRepository.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId)) : null;
        if (term != null && !term.getDepartment().getId().equals(departmentId)) {
            throw new com.internlink.core.shared.exception.BadRequestException("Học kỳ không thuộc khoa");
        }

        int newlyCreated = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();

        for (LecturerImportItem item : items) {
            try {
                String email = item.getEmail().trim().toLowerCase();
                User user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    String bootstrapSecret = SecurePasswordGenerator.generateRandomPassword(32);
                    user = User.builder()
                            .email(email)
                            .fullName(item.getFullName().trim())
                            .phoneNumber(item.getPhoneNumber() != null ? item.getPhoneNumber().trim() : null)
                            .passwordHash(passwordEncoder.encode(bootstrapSecret))
                            .role(UserRole.LECTURER)
                            .department(dept)
                            .mustChangePassword(true)
                            .isActive(true)
                            .build();
                    user = userRepository.save(user);
                    newlyCreated++;
                    emailNotifier.sendProvisionEmail(
                            email,
                            user.getFullName(),
                            activationTokens.issue(user),
                            activationTokens.ttlHours(),
                            term != null ? term.getTermName() : null);
                } else {
                    requireLecturerInDepartment(user, dept);
                    if (user.getDepartment() == null) user.setDepartment(dept);
                    if (item.getPhoneNumber() != null && !item.getPhoneNumber().isBlank()) {
                        user.setPhoneNumber(item.getPhoneNumber().trim());
                    }
                    userRepository.save(user);
                    updated++;
                }

                int maxStudents = item.getMaxStudents() != null && item.getMaxStudents() > 0 ? item.getMaxStudents() : 15;
                if (term != null) {
                    upsertQuota(user, term, maxStudents);
                }
                rosterRepository.linkUnassignedByLecturerEmail(user, email, departmentId);
            } catch (Exception ex) {
                log.error("Lỗi khi import GVHD {}: {}", item.getEmail(), ex.getMessage());
                errors.add(item.getEmail() + ": " + ex.getMessage());
            }
        }

        auditLogService.logAction(actorId, "IMPORT_LECTURERS", "Department", departmentId, "SUCCESS",
                Map.of("total", items.size(), "newlyCreated", newlyCreated, "updated", updated), null);

        return BatchLecturerImportResponse.builder()
                .totalProcessed(items.size())
                .newlyCreatedCount(newlyCreated)
                .updatedCount(updated)
                .errors(errors)
                .build();
    }

    private void requireLecturerInDepartment(User user, Department department) {
        if (user.getRole() != UserRole.LECTURER || user.getDepartment() == null
                || !user.getDepartment().getId().equals(department.getId())) {
            throw new com.internlink.core.shared.exception.BadRequestException(
                    "Email thuộc tài khoản khác vai trò hoặc khoa; liên hệ quản trị viên.");
        }
    }

    private int countAssigned(UUID termId, UUID lecturerId) {
        return rosterRepository.countByAssignedLecturerInTerm(termId).stream()
                .filter(row -> lecturerId.equals(row[0]))
                .mapToInt(row -> ((Long) row[1]).intValue())
                .findFirst().orElse(0);
    }

    private void upsertQuota(User lecturer, InternshipTerm term, int maxStudents) {
        FacultyLecturerQuota quota = quotaRepository.findByLecturerIdAndTermId(lecturer.getId(), term.getId())
                .orElseGet(() -> FacultyLecturerQuota.builder()
                        .lecturer(lecturer)
                        .term(term)
                        .build());
        quota.setMaxStudents(maxStudents);
        quotaRepository.save(quota);
    }
}
