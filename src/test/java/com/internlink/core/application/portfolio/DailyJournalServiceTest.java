package com.internlink.core.application.portfolio;

import static com.internlink.core.application.portfolio.PortfolioTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.internlink.core.application.placement.WeeklyLogbookService;
import com.internlink.core.domain.placement.*;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class DailyJournalServiceTest {
    @Mock JpaDailyJournalRepository journals;
    @Mock JpaInternshipPlacementRepository placements;
    @Mock JpaUserRepository users;
    @Mock JpaWeeklyLogbookRepository weekly;
    @Mock WeeklyLogbookService weeklyService;
    @Mock PortfolioMediaResolver media;
    @Mock SecurityGuard security;

    @Mock
    com.internlink.core.application.system.notification.PlacementWorkflowNotificationHelper
            notifications;

    DailyJournalAggregationService aggregationService;
    DailyJournalService service;
    InternshipPlacement p;

    @BeforeEach
    void setup() {
        p = placement();
        aggregationService = new DailyJournalAggregationService(journals, weekly);
        service = new DailyJournalService(
                journals, placements, users, weekly, weeklyService,
                aggregationService, security, media, notifications);
    }

    void student() {
        when(placements.findLockedById(p.getId())).thenReturn(Optional.of(p));
        when(security.currentUser()).thenReturn(CustomUserDetail.create(p.getStudent()));
        when(users.findById(p.getStudent().getId())).thenReturn(Optional.of(p.getStudent()));
    }

    DailyJournalService.Input input(String attendance, boolean submit) {
        return new DailyJournalService.Input(
                null,
                attendance,
                LocalTime.of(8, 0),
                LocalTime.of(17, 0),
                60,
                2,
                "<p>Lập trình</p>",
                "<p>Hoàn thành API</p>",
                "Nghỉ theo lịch",
                "",
                submit);
    }

    @Test
    void computesHoursAndKeepsSubmissionAwaitingConfirmation() {
        student();
        when(journals.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        var saved = service.save(p.getId(), p.getStartDate(), input("PRESENT", true));
        assertThat(saved.hours()).isEqualByComparingTo("8.00");
        assertThat(saved.status()).isEqualTo("SUBMITTED");
        var leave = service.save(p.getId(), p.getStartDate().plusDays(1), input("LEAVE", true));
        assertThat(leave.hours()).isZero();
        assertThat(leave.sessions()).isZero();
        assertThat(leave.startTime()).isNull();
    }

    @Test
    void submittedWeekPreventsSilentJournalChanges() {
        student();
        var log = WeeklyLogbook.builder().status(LogbookStatus.SUBMITTED).build();
        when(weekly.findByPlacementIdAndWeekNumber(p.getId(), 1)).thenReturn(Optional.of(log));
        assertThatThrownBy(() -> service.save(p.getId(), p.getStartDate(), input("PRESENT", false)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(journals);
    }

    @Test
    void rejectsFutureDateAndStaleVersion() {
        student();
        // "Hôm nay" theo giờ VN như service; CI chạy UTC nên không dùng LocalDate.now() mặc định.
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        p.setEndDate(today.plusDays(3));
        assertThatThrownBy(
                        () ->
                                service.save(
                                        p.getId(),
                                        today.plusDays(1),
                                        input("PRESENT", true)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> DailyJournalService.checkVersion(2, 1))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void onlyConfirmedDaysCountInMentorOnlineMode() {
        p.setSupervisionMode(SupervisionMode.MENTOR_ONLINE);
        var confirmed = new DailyJournal();
        confirmed.setWorkDate(p.getStartDate());
        confirmed.setStatus("CONFIRMED");
        confirmed.setHours(new BigDecimal("8"));
        confirmed.setSessions(2);
        confirmed.setTasks("<b>API</b>");
        var pending = new DailyJournal();
        pending.setWorkDate(p.getStartDate().plusDays(1));
        pending.setStatus("SUBMITTED");
        pending.setHours(new BigDecimal("8"));
        pending.setSessions(2);
        when(journals.findByPlacementIdOrderByWorkDateAsc(p.getId()))
                .thenReturn(List.of(confirmed, pending));
        var week = service.aggregate(p).get(0);
        assertThat(week.hours()).isEqualByComparingTo("8");
        assertThat(week.sessions()).isEqualTo(2);
        assertThat(week.confirmedDays()).isEqualTo(1);
        assertThat(week.submittedDays()).isEqualTo(2);
    }

    @Test
    void submittedDaysCountInAcademicFollowupMode() {
        p.setSupervisionMode(SupervisionMode.ACADEMIC_FOLLOWUP);
        var confirmed = new DailyJournal();
        confirmed.setWorkDate(p.getStartDate());
        confirmed.setStatus("CONFIRMED");
        confirmed.setHours(new BigDecimal("8"));
        confirmed.setSessions(2);
        confirmed.setTasks("<b>API</b>");
        var pending = new DailyJournal();
        pending.setWorkDate(p.getStartDate().plusDays(1));
        pending.setStatus("SUBMITTED");
        pending.setHours(new BigDecimal("8"));
        pending.setSessions(2);
        when(journals.findByPlacementIdOrderByWorkDateAsc(p.getId()))
                .thenReturn(List.of(confirmed, pending));
        var week = service.aggregate(p).get(0);
        assertThat(week.hours()).isEqualByComparingTo("16");
        assertThat(week.sessions()).isEqualTo(4);
        assertThat(week.confirmedDays()).isEqualTo(1);
        assertThat(week.submittedDays()).isEqualTo(2);
    }

    @Test
    void anotherMentorCannotConfirm() {
        var stranger = user(UserRole.COMPANY_MENTOR);
        when(placements.findLockedById(p.getId())).thenReturn(Optional.of(p));
        when(security.currentUser()).thenReturn(CustomUserDetail.create(stranger));
        when(users.findById(stranger.getId())).thenReturn(Optional.of(stranger));
        assertThatThrownBy(
                        () ->
                                service.review(
                                        p.getId(),
                                        p.getStartDate(),
                                        new DailyJournalService.Review("CONFIRMED", "", 0)))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(journals);
    }
}
