package com.internlink.core.application.student.impl;

import com.internlink.core.application.student.StudentProfileService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.student.dto.request.StudentProfileRequest;
import com.internlink.core.presentation.student.dto.response.StudentProfileResponse;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProfileServiceImpl implements StudentProfileService {

    private final JpaStudentProfileRepository studentProfileRepository;
    private final JpaUserRepository userRepository;
    private final JpaAcademicProgramRepository academicProgramRepository;
    private final JpaStudentRosterRepository studentRosterRepository;
    private final JpaDocumentRepository documentRepository;

    @Override
    @Transactional
    public StudentProfileResponse getProfileByUserId(UUID userId) {
        StudentProfile profile = studentProfileRepository.findById(userId)
            .orElseGet(() -> autoHealProfileFromRoster(userId));
        return mapToResponse(profile);
    }

    private StudentProfile autoHealProfileFromRoster(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        var rosterOpt = studentRosterRepository.findFirstByClaimedUserIdOrderByCreatedAtDesc(userId)
            .or(() -> studentRosterRepository.findFirstByOfficialEmailOrderByCreatedAtDesc(user.getEmail().toLowerCase()));

        if (rosterOpt.isPresent()) {
            var roster = rosterOpt.get();
            StudentProfile profile = StudentProfile.builder()
                .userId(userId)
                .user(user)
                .studentCode(roster.getStudentCode())
                .program(roster.getProgram())
                .gpa(roster.getGpa())
                .certificates(java.util.List.of())
                .passedCourses(java.util.List.of())
                .preferences(java.util.Map.of())
                .build();
            return studentProfileRepository.save(profile);
        }

        throw new ResourceNotFoundException("StudentProfile", "userId", userId);
    }

    @Override
    @Transactional
    public StudentProfileResponse createOrUpdateProfile(UUID userId, StudentProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Chỉ tài khoản sinh viên (STUDENT) mới có thể tạo hoặc sửa hồ sơ sinh viên");
        }

        AcademicProgram program = academicProgramRepository.findById(request.getProgramId())
            .orElseThrow(() -> new ResourceNotFoundException("AcademicProgram", "id", request.getProgramId()));

        String studentCode = request.getStudentCode().trim().toUpperCase();
        studentProfileRepository.findByStudentCode(studentCode)
            .filter(existing -> !existing.getUserId().equals(userId))
            .ifPresent(existing -> {
                throw new BadRequestException("Mã số sinh viên '" + studentCode + "' đã được sử dụng");
            });

        StudentProfile profile = studentProfileRepository.findById(userId)
            .orElse(StudentProfile.builder()
                .userId(userId)
                .user(user)
                .build());

        profile.setProgram(program);
        profile.setStudentCode(studentCode);
        profile.setGpa(request.getGpa());
        profile.setBio(request.getBio());
        profile.setCertificates(request.getCertificates() != null ? request.getCertificates() : java.util.List.of());
        profile.setPassedCourses(request.getPassedCourses() != null ? request.getPassedCourses() : java.util.List.of());
        profile.setPreferences(request.getPreferences() != null ? request.getPreferences() : java.util.Map.of());

        StudentProfile saved = studentProfileRepository.save(profile);
        return mapToResponse(saved);
    }

    private StudentProfileResponse mapToResponse(StudentProfile entity) {
        UUID cvDocId = null;
        String cvName = null;
        try {
            List<Document> cvs = documentRepository.findByContextTypeAndContextId(ContextType.PROFILE, entity.getUserId());
            var latestCv = cvs.stream()
                .filter(d -> d.getDocumentType() == DocumentType.CV && "ACTIVE".equals(d.getStatus()) && d.getDeletedAt() == null)
                .max(Comparator.comparing(Document::getCreatedAt));
            if (latestCv.isPresent()) {
                cvDocId = latestCv.get().getId();
                cvName = latestCv.get().getOriginalName();
            }
        } catch (Exception ignored) {
            // Document lookup fallback
        }

        return StudentProfileResponse.builder()
            .userId(entity.getUserId())
            .studentCode(entity.getStudentCode())
            .fullName(entity.getUser().getFullName())
            .email(entity.getUser().getEmail())
            .phoneNumber(entity.getUser().getPhoneNumber())
            .programId(entity.getProgram().getId())
            .programCode(entity.getProgram().getCode())
            .programName(entity.getProgram().getName())
            .departmentName(entity.getProgram().getDepartment().getName())
            .gpa(entity.getGpa())
            .bio(entity.getBio())
            .certificates(entity.getCertificates())
            .passedCourses(entity.getPassedCourses())
            .preferences(entity.getPreferences())
            .cvDocumentId(cvDocId)
            .cvFileName(cvName)
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
