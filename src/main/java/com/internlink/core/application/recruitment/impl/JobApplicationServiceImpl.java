package com.internlink.core.application.recruitment.impl;

import com.internlink.core.application.recruitment.JobApplicationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobPositionRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest;
import com.internlink.core.presentation.recruitment.dto.request.RecordInterviewResultRequest;
import com.internlink.core.presentation.recruitment.dto.request.ScheduleInterviewRequest;
import com.internlink.core.presentation.recruitment.dto.response.JobApplicationResponse;
import com.internlink.core.shared.api.PageParam;
import com.internlink.core.shared.api.PageResponse;
import com.internlink.core.shared.api.PaginatedResponseMapper;
import com.internlink.core.shared.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import com.internlink.core.shared.enums.InterviewFormat;
import com.internlink.core.shared.enums.InterviewRoundResult;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JpaJobApplicationRepository applicationRepository;
    private final JpaJobPositionRepository jobPositionRepository;
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;
    private final com.internlink.core.application.system.NotificationService notifications;
    private final PaginatedResponseMapper paginatedResponseMapper;
    private final InterviewInvitationNotifier interviewNotifier;
    private final JobApplicationSubmissionValidator submissionValidator;
    private final JobApplicationMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getApplicationsByStudent(UUID studentId) {
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor) || actor.getId().equals(studentId));
        return applicationRepository.findByStudentId(studentId).stream()
            .map(mapper::map)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JobApplicationResponse> getApplicationsByStudentPage(UUID studentId, PageParam pageParam) {
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor) || actor.getId().equals(studentId));
        Page<JobApplication> page = applicationRepository.findByStudentId(studentId, pageParam.toPageable());
        return paginatedResponseMapper.toPageResponse(page, mapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getApplicationsByJob(UUID jobId) {
        JobPosition job = jobPositionRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", jobId));
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.representsCompany(actor, job.getCompany().getId())
            || ResourceAuthorization.actsForSchool(actor));
        return applicationRepository.findByJobId(jobId).stream()
            .map(mapper::map)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JobApplicationResponse> getApplicationsByJobPage(UUID jobId, PageParam pageParam) {
        JobPosition job = jobPositionRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", jobId));
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.representsCompany(actor, job.getCompany().getId())
            || ResourceAuthorization.actsForSchool(actor));
        Page<JobApplication> page = applicationRepository.findByJobId(jobId, pageParam.toPageable());
        return paginatedResponseMapper.toPageResponse(page, mapper::map);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getApplicationsForMyCompany() {
        User actor = currentActor();
        ResourceAuthorization.require(actor.getRole() == UserRole.COMPANY_REP && actor.getCompany() != null);
        return applicationRepository.findByJobCompanyId(actor.getCompany().getId()).stream()
            .map(mapper::map)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public JobApplicationResponse getApplicationById(UUID id) {
        JobApplication app = applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobApplication", "id", id));
        ResourceAuthorization.require(ResourceAuthorization.canReadApplication(currentActor(), app));
        return mapper.map(app);
    }

    @Override
    @Transactional
    public JobApplicationResponse applyJob(UUID studentId, JobApplicationRequest request) {
        User student = userRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", studentId));
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("Chỉ sinh viên mới được nộp hồ sơ ứng tuyển");
        }

        JobPosition job = jobPositionRepository.findById(request.getJobId())
            .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", request.getJobId()));

        Document cvDoc = submissionValidator.validate(studentId, request, job);

        JobApplication application = JobApplication.builder()
            .job(job)
            .student(student)
            .submittedCvDocument(cvDoc)
            .coverLetter(request.getCoverLetter())
            .status(ApplicationStatus.SUBMITTED)
            .interviewRounds(List.of())
            .build();

        JobApplication saved = applicationRepository.save(application);
        notifyCompany(saved, "APPLICATION_SUBMITTED", "Hồ sơ ứng tuyển mới",
            student.getFullName() + " đã ứng tuyển vị trí " + job.getTitle());
        return mapper.map(saved);
    }

    @Override
    @Transactional
    public JobApplicationResponse updateApplicationStatus(UUID id, ApplicationStatus status) {
        JobApplication app = applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobApplication", "id", id));
        TermGuard.requireNotClosed(app.getJob().getTerm());
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor)
            || ResourceAuthorization.representsCompany(actor, app.getJob().getCompany().getId()));

        if (!isValidTransition(app.getStatus(), status)) {
            throw new BadRequestException("Chuyển trạng thái hồ sơ không hợp lệ: " + app.getStatus() + " -> " + status);
        }

        app.setStatus(status);
        JobApplication saved = applicationRepository.save(app);
        notifications.sendNotification(saved.getStudent().getId(), "APPLICATION_STATUS_CHANGED", "Cập nhật hồ sơ ứng tuyển",
            "Hồ sơ vị trí " + saved.getJob().getTitle() + " chuyển sang " + status.name() + ".", "/student/applications");
        return mapper.map(saved);
    }

    @Override
    @Transactional
    public JobApplicationResponse scheduleInterview(UUID id, ScheduleInterviewRequest request) {
        JobApplication app = applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobApplication", "id", id));
        TermGuard.requireNotClosed(app.getJob().getTerm());
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor)
            || ResourceAuthorization.representsCompany(actor, app.getJob().getCompany().getId()));

        if (app.getStatus() == ApplicationStatus.OFFERED
            || app.getStatus() == ApplicationStatus.REJECTED
            || app.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new BadRequestException("Không thể lên lịch phỏng vấn cho hồ sơ ở trạng thái " + app.getStatus());
        }

        if (request.getFormat() == InterviewFormat.ONLINE) {
            if (request.getMeetingUrl() == null || request.getMeetingUrl().isBlank()) {
                throw new BadRequestException("Phỏng vấn trực tuyến (Online) bắt buộc phải có liên kết phòng họp (Google Meet, MS Teams, Zoom)");
            }
        } else if (request.getFormat() == InterviewFormat.OFFLINE) {
            if (request.getLocation() == null || request.getLocation().isBlank()) {
                throw new BadRequestException("Phỏng vấn trực tiếp (Offline) bắt buộc phải có địa điểm");
            }
        }

        List<Map<String, Object>> rounds = new ArrayList<>(app.getInterviewRounds() != null ? app.getInterviewRounds() : List.of());
        int roundNum = request.getRoundNumber() != null ? request.getRoundNumber() : rounds.size() + 1;

        Map<String, Object> roundMap = new LinkedHashMap<>();
        roundMap.put("roundNumber", roundNum);
        roundMap.put("format", request.getFormat().name());
        roundMap.put("scheduledAt", request.getScheduledAt().toString());
        roundMap.put("meetingUrl", request.getMeetingUrl());
        roundMap.put("location", request.getLocation());
        roundMap.put("interviewer", request.getInterviewer());
        roundMap.put("notes", request.getNotes());
        roundMap.put("result", InterviewRoundResult.PENDING.name());
        roundMap.put("feedback", null);
        roundMap.put("scheduledBy", actor.getId().toString());
        roundMap.put("createdAt", OffsetDateTime.now().toString());

        rounds.add(roundMap);
        app.setInterviewRounds(rounds);
        app.setStatus(ApplicationStatus.INTERVIEWING);

        JobApplication saved = applicationRepository.save(app);

        String companyName = app.getJob().getCompany().getCompanyName();
        String jobTitle = app.getJob().getTitle();
        String timeStr = request.getScheduledAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        String notifMsg = request.getFormat() == InterviewFormat.ONLINE
            ? String.format("Công ty %s mời bạn phỏng vấn Online vị trí '%s' vào lúc %s. Link phòng họp: %s", companyName, jobTitle, timeStr, request.getMeetingUrl())
            : String.format("Công ty %s mời bạn phỏng vấn vị trí '%s' vào lúc %s tại %s", companyName, jobTitle, timeStr, request.getLocation());

        interviewNotifier.send(saved, actor, request, roundNum, timeStr, notifMsg);

        return mapper.map(saved);
    }

    @Override
    @Transactional
    public JobApplicationResponse recordInterviewResult(UUID id, int roundNumber, RecordInterviewResultRequest request) {
        JobApplication app = applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobApplication", "id", id));
        TermGuard.requireNotClosed(app.getJob().getTerm());
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor)
            || ResourceAuthorization.representsCompany(actor, app.getJob().getCompany().getId()));

        List<Map<String, Object>> rounds = new ArrayList<>(app.getInterviewRounds() != null ? app.getInterviewRounds() : List.of());
        boolean found = false;
        for (Map<String, Object> round : rounds) {
            Object numObj = round.get("roundNumber");
            if (numObj != null && Integer.parseInt(numObj.toString()) == roundNumber) {
                round.put("result", request.getResult().name());
                round.put("feedback", request.getFeedback());
                round.put("recordedAt", OffsetDateTime.now().toString());
                found = true;
                break;
            }
        }

        if (!found) {
            throw new ResourceNotFoundException("InterviewRound", "roundNumber", roundNumber);
        }

        app.setInterviewRounds(rounds);
        if (request.getResult() == InterviewRoundResult.FAILED) {
            app.setStatus(ApplicationStatus.REJECTED);
        }

        JobApplication saved = applicationRepository.save(app);

        String resultText = request.getResult() == InterviewRoundResult.PASSED ? "ĐẠT" : "CHƯA PHÙ HỢP";
        notifications.sendNotification(
            saved.getStudent().getId(),
            "INTERVIEW_RESULT",
            "Kết quả phỏng vấn: " + saved.getJob().getTitle(),
            String.format("Kết quả vòng %d vị trí '%s': %s.", roundNumber, saved.getJob().getTitle(), resultText),
            "/student/applications"
        );

        return mapper.map(saved);
    }

    @Override
    @Transactional
    public JobApplicationResponse withdrawApplication(UUID id) {
        JobApplication app = applicationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("JobApplication", "id", id));
        TermGuard.requireNotClosed(app.getJob().getTerm());
        User actor = currentActor();
        ResourceAuthorization.require(actor.getRole() == UserRole.STUDENT
            && actor.getId().equals(app.getStudent().getId()));
        if (app.getStatus() != ApplicationStatus.SUBMITTED
            && app.getStatus() != ApplicationStatus.REVIEWING
            && app.getStatus() != ApplicationStatus.INTERVIEWING) {
            throw new BadRequestException("Chỉ có thể rút hồ sơ trước khi nhận offer");
        }
        app.setStatus(ApplicationStatus.WITHDRAWN);
        JobApplication saved = applicationRepository.save(app);
        notifyCompany(saved, "APPLICATION_WITHDRAWN", "Sinh viên rút hồ sơ",
            saved.getStudent().getFullName() + " đã rút hồ sơ vị trí " + saved.getJob().getTitle());
        return mapper.map(saved);
    }

    private void notifyCompany(JobApplication app, String type, String title, String message) {
        userRepository.findByCompanyIdAndRole(app.getJob().getCompany().getId(), UserRole.COMPANY_REP)
            .forEach(user -> notifications.sendNotification(user.getId(), type, title, message, "/company/candidates"));
    }

    private User currentActor() {
        UUID actorId = securityGuard.currentUser().getId();
        return userRepository.findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
    }

    private boolean isValidTransition(ApplicationStatus current, ApplicationStatus next) {
        return switch (current) {
            case SUBMITTED -> next == ApplicationStatus.REVIEWING || next == ApplicationStatus.REJECTED;
            case REVIEWING -> next == ApplicationStatus.INTERVIEWING || next == ApplicationStatus.REJECTED;
            case INTERVIEWING -> next == ApplicationStatus.REJECTED;
            case OFFERED -> false;
            case REJECTED, WITHDRAWN -> false;
        };
    }

}
