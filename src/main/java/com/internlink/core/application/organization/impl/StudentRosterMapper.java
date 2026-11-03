package com.internlink.core.application.organization.impl;

import com.internlink.core.domain.placement.InternshipDurationRule;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import org.springframework.stereotype.Component;

/**
 * Đảm nhiệm duy nhất 1 nhiệm vụ: Chuyển đổi giữa entity StudentRoster và DTO Response.
 */
@Component
public class StudentRosterMapper {

    public StudentRosterResponse mapToResponse(StudentRoster entity) {
        return StudentRosterResponse.builder()
                .id(entity.getId())
                .termId(entity.getTerm().getId())
                .termName(entity.getTerm().getTermName())
                .programId(entity.getProgram().getId())
                .programName(entity.getProgram().getName())
                .studentCode(entity.getStudentCode())
                .officialEmail(entity.getOfficialEmail())
                .fullName(entity.getFullName())
                .academicYear(entity.getAcademicYear())
                .classCode(entity.getClassCode())
                .internshipCourseCode(entity.getInternshipCourseCode())
                .internshipCredits(entity.getInternshipCredits())
                .requiredWeeks(InternshipDurationRule.requiredWeeks(entity.getInternshipCredits()))
                .claimedUserId(entity.getClaimedUser() != null ? entity.getClaimedUser().getId() : null)
                .claimedAt(entity.getClaimedAt())
                .assignedLecturerId(entity.getAssignedLecturer() != null ? entity.getAssignedLecturer().getId() : null)
                .lecturerCode(entity.getLecturerCode())
                .lecturerName(entity.getLecturerName() != null ? entity.getLecturerName()
                        : (entity.getAssignedLecturer() != null ? entity.getAssignedLecturer().getFullName() : null))
                .lecturerEmail(entity.getLecturerEmail() != null ? entity.getLecturerEmail()
                        : (entity.getAssignedLecturer() != null ? entity.getAssignedLecturer().getEmail() : null))
                .letterReceived(entity.isLetterReceived())
                .letterReceivedAt(entity.getLetterReceivedAt())
                .letterNotes(entity.getLetterNotes())
                .gpa(entity.getGpa())
                .phoneNumber(entity.getPhoneNumber())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
