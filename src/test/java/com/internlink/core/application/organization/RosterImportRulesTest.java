package com.internlink.core.application.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.internlink.core.application.organization.RosterImportRules.Action;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.shared.enums.RosterSource;

class RosterImportRulesTest {
    private final Department cnpm = department();

    @Test
    void newStudentIsCreatedForAnyone() {
        assertThat(RosterImportRules.decide(null, null)).isEqualTo(Action.CREATE);
        assertThat(RosterImportRules.decide(cnpm.getId(), null)).isEqualTo(Action.CREATE);
    }

    @Test
    void schoolOfficeAlwaysUpdates() {
        assertThat(RosterImportRules.decide(null, roster(RosterSource.FACULTY))).isEqualTo(Action.UPDATE);
        assertThat(RosterImportRules.decide(null, roster(RosterSource.SCHOOL_OFFICE))).isEqualTo(Action.UPDATE);
    }

    @Test
    void facultyUpdatesOwnAdditionsButOnlyLecturerOnSchoolOfficeRows() {
        assertThat(RosterImportRules.decide(cnpm.getId(), roster(RosterSource.FACULTY))).isEqualTo(Action.UPDATE);
        assertThat(RosterImportRules.decide(cnpm.getId(), roster(RosterSource.SCHOOL_OFFICE)))
                .isEqualTo(Action.LECTURER_ONLY);
    }

    @Test
    void facultySkipsStudentOfAnotherDepartment() {
        assertThat(RosterImportRules.decide(UUID.randomUUID(), roster(RosterSource.FACULTY)))
                .isEqualTo(Action.SKIP_OTHER_DEPARTMENT);
    }

    @Test
    void sourceFollowsActorScope() {
        assertThat(RosterImportRules.sourceFor(null)).isEqualTo(RosterSource.SCHOOL_OFFICE);
        assertThat(RosterImportRules.sourceFor(cnpm.getId())).isEqualTo(RosterSource.FACULTY);
    }

    private StudentRoster roster(RosterSource source) {
        AcademicProgram program = AcademicProgram.builder().department(cnpm).code("SE").name("SE").build();
        return StudentRoster.builder().program(program).studentCode("B01").source(source).build();
    }

    private static Department department() {
        Department value = Department.builder().code("CNPM").name("CNPM").build();
        value.setId(UUID.randomUUID());
        return value;
    }
}
