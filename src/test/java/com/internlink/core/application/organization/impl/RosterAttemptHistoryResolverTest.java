package com.internlink.core.application.organization.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.evaluation.FinalResult;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaFinalResultRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.presentation.organization.dto.response.RosterPriorAttemptResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.ResultStatus;

@ExtendWith(MockitoExtension.class)
class RosterAttemptHistoryResolverTest {

    @Mock JpaStudentRosterRepository rosterRepository;
    @Mock JpaInternshipPlacementRepository placementRepository;
    @Mock JpaFinalResultRepository finalResultRepository;
    @InjectMocks RosterAttemptHistoryResolver resolver;

    private final Department department = withId(Department.builder().build());
    private final InternshipTerm hk2 = term("HK2", LocalDate.of(2026, 1, 5));
    private final InternshipTerm hk3 = term("HK3", LocalDate.of(2026, 6, 1));
    private final User student = withId(new User());

    @Test
    void studentWhoDroppedLastTermIsMarkedAsSecondAttempt() {
        StudentRoster current = roster(hk3, "B2110954", student);
        StudentRoster previous = roster(hk2, "B2110954", null); // dòng cũ chưa cấp tài khoản
        InternshipPlacement terminated = placement(hk2, PlacementStatus.TERMINATED);
        when(rosterRepository.findInOtherTermsByStudentCodes(anyCollection(), eq(department.getId()), eq(hk3.getId())))
                .thenReturn(List.of(previous));
        when(placementRepository.findByStudentIdsAndTermIds(anyCollection(), anyCollection()))
                .thenReturn(List.of(terminated));

        StudentRosterResponse response = StudentRosterResponse.builder().studentCode("B2110954").build();
        resolver.enrich(hk3, List.of(current), List.of(response));

        assertThat(response.getAttemptNumber()).isEqualTo(2);
        assertThat(response.getPriorAttempts())
                .containsExactly(new RosterPriorAttemptResponse(hk2.getId(), "HK2", RosterAttemptHistoryResolver.TERMINATED));
    }

    @Test
    void laterTermsDoNotCountAsPriorAttempts() {
        StudentRoster current = roster(hk2, "B2110954", student);
        when(rosterRepository.findInOtherTermsByStudentCodes(anyCollection(), any(), any()))
                .thenReturn(List.of(roster(hk3, "B2110954", student)));

        StudentRosterResponse response = StudentRosterResponse.builder().studentCode("B2110954").build();
        resolver.enrich(hk2, List.of(current), List.of(response));

        assertThat(response.getAttemptNumber()).isEqualTo(1);
        assertThat(response.getPriorAttempts()).isEmpty();
        verify(placementRepository, never()).findByStudentIdsAndTermIds(anyCollection(), anyCollection());
    }

    @Test
    void outcomeReflectsWhatHappenedInThatTerm() {
        InternshipPlacement completed = placement(hk2, PlacementStatus.COMPLETED);
        FinalResult failed = FinalResult.builder().placement(completed).resultStatus(ResultStatus.FAILED).build();
        FinalResult passed = FinalResult.builder().placement(completed).resultStatus(ResultStatus.PASSED).build();

        assertThat(RosterAttemptHistoryResolver.outcomeOf(List.of(), Map.of()))
                .isEqualTo(RosterAttemptHistoryResolver.NO_PLACEMENT);
        assertThat(RosterAttemptHistoryResolver.outcomeOf(List.of(completed), Map.of(completed.getId(), failed)))
                .isEqualTo(RosterAttemptHistoryResolver.FAILED);
        assertThat(RosterAttemptHistoryResolver.outcomeOf(List.of(completed), Map.of(completed.getId(), passed)))
                .isEqualTo(RosterAttemptHistoryResolver.PASSED);
        assertThat(RosterAttemptHistoryResolver.outcomeOf(
                List.of(placement(hk2, PlacementStatus.TERMINATED), placement(hk2, PlacementStatus.PREPARING)), Map.of()))
                .isEqualTo(RosterAttemptHistoryResolver.IN_PROGRESS);
    }

    private InternshipTerm term(String name, LocalDate start) {
        return withId(InternshipTerm.builder().termName(name).startDate(start).department(department).build());
    }

    private StudentRoster roster(InternshipTerm term, String code, User claimedUser) {
        return withId(StudentRoster.builder().term(term).studentCode(code).claimedUser(claimedUser).build());
    }

    private InternshipPlacement placement(InternshipTerm term, PlacementStatus status) {
        return withId(InternshipPlacement.builder().term(term).student(student).status(status).build());
    }

    private static <T extends com.internlink.core.shared.domain.BaseEntity> T withId(T entity) {
        entity.setId(UUID.randomUUID());
        return entity;
    }
}
