package com.internlink.core.application.student;

import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaSavedJobRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentSkillRepository;
import com.internlink.core.presentation.student.dto.response.JobSearchContextResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.TermStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Gom dữ liệu cá nhân của sinh viên đang đăng nhập cho bộ lọc thông minh ở trang tìm việc. */
@Service
@RequiredArgsConstructor
public class JobSearchContextService {

    /** Kỳ còn ý nghĩa cho việc tìm nơi thực tập; kỳ nháp hoặc đã đóng thì bỏ qua. */
    private static final Set<TermStatus> SEARCHABLE_TERMS =
            Set.of(TermStatus.REGISTRATION_OPEN, TermStatus.APPLICATION_OPEN, TermStatus.ACTIVE);

    private final JpaStudentProfileRepository profiles;
    private final JpaStudentRosterRepository rosters;
    private final JpaStudentSkillRepository skills;
    private final JpaJobApplicationRepository applications;
    private final JpaSavedJobRepository savedJobs;

    @Transactional(readOnly = true)
    public JobSearchContextResponse forStudent(UUID studentId) {
        StudentProfile profile = profiles.findByUserId(studentId).orElse(null);
        StudentRoster roster = rosters.findFirstByClaimedUserIdOrderByCreatedAtDesc(studentId).orElse(null);
        InternshipTerm term = Optional.ofNullable(roster)
                .map(StudentRoster::getTerm)
                .filter(t -> SEARCHABLE_TERMS.contains(t.getStatus()))
                .orElse(null);
        AcademicProgram program = profile != null && profile.getProgram() != null
                ? profile.getProgram()
                : roster == null ? null : roster.getProgram();
        Map<String, Object> preferences = profile == null || profile.getPreferences() == null
                ? Map.of()
                : profile.getPreferences();

        return new JobSearchContextResponse(
                program == null ? null : program.getCode(),
                program == null ? null : program.getName(),
                skills.findByIdStudentIdAndIsConfirmedTrue(studentId).stream()
                        .map(skill -> skill.getSkill().getId())
                        .distinct()
                        .toList(),
                applications.findJobIdsByStudentId(studentId),
                savedJobs.findJobIdsByStudentIdAndJobStatus(studentId, JobStatus.APPROVED),
                term == null ? null : term.getId(),
                term == null ? null : term.getTermName(),
                preferences);
    }
}
