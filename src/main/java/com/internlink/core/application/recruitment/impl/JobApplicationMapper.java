package com.internlink.core.application.recruitment.impl;

import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.presentation.recruitment.dto.response.*;
import com.internlink.core.shared.enums.*;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.*;

@Component
public class JobApplicationMapper {
    /** Bản cho DN: kèm MSSV và ngành từ hồ sơ SV (có thể null nếu SV chưa có hồ sơ). */
    public JobApplicationResponse map(JobApplication entity, StudentProfile profile) {
        JobApplicationResponse response = map(entity);
        if (profile != null) {
            response.setStudentCode(profile.getStudentCode());
            response.setProgramName(profile.getProgram() != null ? profile.getProgram().getName() : null);
        }
        return response;
    }

    public JobApplicationResponse map(JobApplication entity) {
        var rounds = new ArrayList<InterviewRoundDto>();
        if (entity.getInterviewRounds() != null)
            entity.getInterviewRounds().forEach(value -> mapRound(value).ifPresent(rounds::add));
        var job = entity.getJob();
        var term = job != null ? job.getTerm() : null;
        return JobApplicationResponse.builder()
                .id(entity.getId()).jobId(job != null ? job.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .companyName(job != null && job.getCompany() != null ? job.getCompany().getCompanyName() : null)
                .studentId(entity.getStudent() != null ? entity.getStudent().getId() : null)
                .studentName(entity.getStudent() != null ? entity.getStudent().getFullName() : null)
                .studentEmail(entity.getStudent() != null ? entity.getStudent().getEmail() : null)
                .submittedCvDocumentId(entity.getSubmittedCvDocument() != null ? entity.getSubmittedCvDocument().getId() : null)
                .coverLetter(entity.getCoverLetter()).aiMatchDetail(entity.getAiMatchDetail()).interviewRounds(rounds)
                .termId(term != null ? term.getId() : null).termName(term != null ? term.getTermName() : null)
                .academicYear(term != null ? term.getAcademicYear() : null).semester(term != null ? term.getSemester() : null)
                .status(entity.getStatus()).submittedAt(entity.getSubmittedAt()).updatedAt(entity.getUpdatedAt()).build();
    }

    private Optional<InterviewRoundDto> mapRound(Map<String, Object> value) {
        try {
            return Optional.of(InterviewRoundDto.builder()
                    .roundNumber(integer(value.get("roundNumber")))
                    .format(enumValue(InterviewFormat.class, value.get("format")))
                    .scheduledAt(date(value.get("scheduledAt")))
                    .meetingUrl(text(value.get("meetingUrl"))).location(text(value.get("location")))
                    .interviewer(text(value.get("interviewer"))).notes(text(value.get("notes")))
                    .result(enumValue(InterviewRoundResult.class, value.get("result")))
                    .feedback(text(value.get("feedback"))).recordedAt(date(value.get("recordedAt"))).build());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private Integer integer(Object value) { return value == null ? null : Integer.valueOf(value.toString()); }
    private String text(Object value) { return Objects.toString(value, null); }
    private OffsetDateTime date(Object value) { return value == null ? null : OffsetDateTime.parse(value.toString()); }
    private <T extends Enum<T>> T enumValue(Class<T> type, Object value) {
        return value == null ? null : Enum.valueOf(type, value.toString());
    }
}
