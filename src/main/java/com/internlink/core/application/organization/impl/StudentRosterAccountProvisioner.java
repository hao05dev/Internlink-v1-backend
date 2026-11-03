package com.internlink.core.application.organization.impl;

import com.internlink.core.application.auth.AccountActivationTokenService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.organization.dto.response.BatchProvisionResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.util.SecurePasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import com.internlink.core.application.organization.RosterSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Đảm nhiệm duy nhất 1 nhiệm vụ: Cấp tài khoản hệ thống (đơn lẻ hoặc hàng loạt) cho sinh viên đủ điều kiện,
 * tạo thông tin đăng nhập bất khả dụng và gửi liên kết kích hoạt một lần.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudentRosterAccountProvisioner {

    private final JpaStudentRosterRepository rosterRepository;
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final StudentProvisionEmailNotifier emailNotifier;
    private final JpaStudentProfileRepository studentProfileRepository;
    private final AccountActivationTokenService activationTokens;

    public StudentRosterResponse provisionSingleAccount(StudentRoster roster, StudentRosterMapper mapper) {
        if (roster.getClaimedUser() != null) {
            throw new BadRequestException("Sinh viên này đã có tài khoản hệ thống (MSSV: " + roster.getStudentCode() + ")");
        }

        String email = roster.getOfficialEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            User existing = userRepository.findByEmail(email).orElseThrow();
                requireStudentInDepartment(existing, roster);
            roster.setClaimedUser(existing);
            roster.setClaimedAt(OffsetDateTime.now());
            rosterRepository.save(roster);
            ensureStudentProfile(existing, roster);
            sendActivationIfNeverActivated(existing, roster, roster.getTerm().getTermName());
            auditLogService.logAction(
                    securityGuard.currentUser().getId(), "LINK_ROSTER_TO_EXISTING_USER",
                    "StudentRoster", roster.getId(), "SUCCESS",
                    Map.of("studentCode", roster.getStudentCode(), "userId", existing.getId()), null);
            return mapper.mapToResponse(roster);
        }

        String bootstrapSecret = SecurePasswordGenerator.generateRandomPassword(32);
        User newUser = User.builder()
                .email(email)
                .fullName(roster.getFullName())
                .phoneNumber(roster.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(bootstrapSecret))
                .role(UserRole.STUDENT)
                .department(roster.getTerm().getDepartment())
                .mustChangePassword(true)
                .isActive(true)
                .build();
        User saved = userRepository.save(newUser);

        roster.setClaimedUser(saved);
        roster.setClaimedAt(OffsetDateTime.now());
        rosterRepository.save(roster);
        ensureStudentProfile(saved, roster);

        sendActivation(saved, roster, roster.getTerm().getTermName());

        auditLogService.logAction(
                securityGuard.currentUser().getId(), "PROVISION_STUDENT_ACCOUNT",
                "StudentRoster", roster.getId(), "SUCCESS",
                Map.of("studentCode", roster.getStudentCode(), "email", email), null);

        return mapper.mapToResponse(roster);
    }

    public BatchProvisionResponse batchProvisionEligible(InternshipTerm term, UUID actorId, Collection<UUID> rosterIds) {
        List<StudentRoster> withoutAccount = rosterRepository.findByTermId(term.getId()).stream()
                .filter(RosterSelection.of(rosterIds))
                .filter(r -> r.getClaimedUser() == null)
                .toList();

        if (withoutAccount.isEmpty()) {
            return BatchProvisionResponse.builder()
                    .totalWithoutAccount(0)
                    .newlyCreatedCount(0)
                    .linkedExistingCount(0)
                    .accounts(List.of())
                    .build();
        }

        int newlyCreated = 0;
        int linkedExisting = 0;
        List<BatchProvisionResponse.ProvisionedStudentAccountDto> accountDtos = new ArrayList<>();
        List<StudentRoster> rostersToUpdate = new ArrayList<>();

        for (StudentRoster roster : withoutAccount) {
            String email = roster.getOfficialEmail().trim().toLowerCase();
            if (userRepository.existsByEmail(email)) {
                User existing = userRepository.findByEmail(email).orElseThrow();
                requireStudentInDepartment(existing, roster);
                roster.setClaimedUser(existing);
                roster.setClaimedAt(OffsetDateTime.now());
                rostersToUpdate.add(roster);
                ensureStudentProfile(existing, roster);
                sendActivationIfNeverActivated(existing, roster, term.getTermName());
                linkedExisting++;
                accountDtos.add(BatchProvisionResponse.ProvisionedStudentAccountDto.builder()
                        .studentCode(roster.getStudentCode())
                        .fullName(roster.getFullName())
                        .officialEmail(email)
                        .isNewlyCreated(false)
                        .build());
            } else {
                String bootstrapSecret = SecurePasswordGenerator.generateRandomPassword(32);
                User newUser = User.builder()
                        .email(email)
                        .fullName(roster.getFullName())
                        .phoneNumber(roster.getPhoneNumber())
                        .passwordHash(passwordEncoder.encode(bootstrapSecret))
                        .role(UserRole.STUDENT)
                        .department(term.getDepartment())
                        .mustChangePassword(true)
                        .isActive(true)
                        .build();
                User saved = userRepository.save(newUser);

                roster.setClaimedUser(saved);
                roster.setClaimedAt(OffsetDateTime.now());
                rostersToUpdate.add(roster);
                ensureStudentProfile(saved, roster);
                newlyCreated++;

                sendActivation(saved, roster, term.getTermName());

                accountDtos.add(BatchProvisionResponse.ProvisionedStudentAccountDto.builder()
                        .studentCode(roster.getStudentCode())
                        .fullName(roster.getFullName())
                        .officialEmail(email)
                        .isNewlyCreated(true)
                        .build());
            }
        }

        rosterRepository.saveAll(rostersToUpdate);
        auditLogService.logAction(
                actorId, "BATCH_PROVISION_STUDENT_ACCOUNTS", "InternshipTerm", term.getId(), "SUCCESS",
                Map.of("total", withoutAccount.size(), "newlyCreated", newlyCreated, "linkedExisting", linkedExisting), null);

        return BatchProvisionResponse.builder()
                .totalWithoutAccount(withoutAccount.size())
                .newlyCreatedCount(newlyCreated)
                .linkedExistingCount(linkedExisting)
                .accounts(accountDtos)
                .build();
    }

    /**
     * Tài khoản có sẵn (tạo ngoài luồng cấp phát) mà chưa từng đăng nhập hay đặt mật khẩu thì sinh viên
     * không biết mật khẩu, nên vẫn cần liên kết kích hoạt như tài khoản mới.
     */
    private void sendActivationIfNeverActivated(User user, StudentRoster roster, String termName) {
        if (user.getLastLoginAt() == null && user.getPasswordChangedAt() == null) {
            sendActivation(user, roster, termName);
        }
    }

    private void sendActivation(User user, StudentRoster roster, String termName) {
        emailNotifier.sendProvisionEmail(
                user.getEmail(),
                roster.getFullName(),
                roster.getStudentCode(),
                activationTokens.issue(user),
                activationTokens.ttlHours(),
                termName);
    }

    private void requireStudentInDepartment(User user, StudentRoster roster) {
        if (user.getRole() != UserRole.STUDENT || user.getDepartment() == null
                || !user.getDepartment().getId().equals(roster.getTerm().getDepartment().getId())) {
            throw new BadRequestException("Email thuộc tài khoản khác vai trò hoặc khoa; liên hệ quản trị viên.");
        }
    }

    private void ensureStudentProfile(User user, StudentRoster roster) {
        StudentProfile profile = studentProfileRepository.findById(user.getId())
                .orElseGet(() -> StudentProfile.builder()
                        .userId(user.getId())
                        .user(user)
                        .certificates(java.util.List.of())
                        .passedCourses(java.util.List.of())
                        .preferences(java.util.Map.of())
                        .build());

        boolean changed = false;
        if (profile.getStudentCode() == null || profile.getStudentCode().isBlank()) {
            profile.setStudentCode(roster.getStudentCode());
            changed = true;
        }
        if (profile.getProgram() == null && roster.getProgram() != null) {
            profile.setProgram(roster.getProgram());
            changed = true;
        }
        if (profile.getGpa() == null && roster.getGpa() != null) {
            profile.setGpa(roster.getGpa());
            changed = true;
        }
        if (changed || profile.getVersion() == null) {
            studentProfileRepository.save(profile);
        }
    }
}
