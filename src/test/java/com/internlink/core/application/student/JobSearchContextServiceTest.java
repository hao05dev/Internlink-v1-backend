package com.internlink.core.application.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.internlink.core.domain.ai_matching.SkillTaxonomy;
import com.internlink.core.domain.ai_matching.StudentSkill;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaSavedJobRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentSkillRepository;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.TermStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSearchContextServiceTest {

    @Mock JpaStudentProfileRepository profiles;
    @Mock JpaStudentRosterRepository rosters;
    @Mock JpaStudentSkillRepository skills;
    @Mock JpaJobApplicationRepository applications;
    @Mock JpaSavedJobRepository savedJobs;

    private final UUID studentId = UUID.randomUUID();
    private JobSearchContextService service;

    @BeforeEach
    void setUp() {
        service = new JobSearchContextService(profiles, rosters, skills, applications, savedJobs);
    }

    @Test
    void gathersProgramSkillsApplicationsSavedJobsAndOpenTerm() {
        UUID applied = UUID.randomUUID();
        UUID saved = UUID.randomUUID();
        StudentProfile profile = new StudentProfile();
        profile.setProgram(program("SE", "Kỹ thuật phần mềm"));
        profile.setPreferences(Map.of("preferredLocations", List.of("Cần Thơ")));
        InternshipTerm term = term(TermStatus.APPLICATION_OPEN);

        when(profiles.findByUserId(studentId)).thenReturn(Optional.of(profile));
        when(rosters.findFirstByClaimedUserIdOrderByCreatedAtDesc(studentId)).thenReturn(Optional.of(roster(term, null)));
        when(skills.findByIdStudentIdAndIsConfirmedTrue(studentId)).thenReturn(List.of(skill("java"), skill("java"), skill("sql")));
        when(applications.findJobIdsByStudentId(studentId)).thenReturn(List.of(applied));
        when(savedJobs.findJobIdsByStudentIdAndJobStatus(studentId, JobStatus.APPROVED)).thenReturn(List.of(saved));

        var context = service.forStudent(studentId);

        assertThat(context.programCode()).isEqualTo("SE");
        assertThat(context.programName()).isEqualTo("Kỹ thuật phần mềm");
        assertThat(context.confirmedSkillIds()).containsExactly("java", "sql");
        assertThat(context.appliedJobIds()).containsExactly(applied);
        assertThat(context.savedJobIds()).containsExactly(saved);
        assertThat(context.activeTermId()).isEqualTo(term.getId());
        assertThat(context.activeTermName()).isEqualTo("HK1 2026-2027");
        assertThat(context.preferences()).containsKey("preferredLocations");
    }

    @Test
    void fallsBackToRosterProgramAndHidesClosedTerm() {
        when(profiles.findByUserId(studentId)).thenReturn(Optional.empty());
        when(rosters.findFirstByClaimedUserIdOrderByCreatedAtDesc(studentId))
                .thenReturn(Optional.of(roster(term(TermStatus.CLOSED), program("IT", "Công nghệ thông tin"))));
        when(skills.findByIdStudentIdAndIsConfirmedTrue(studentId)).thenReturn(List.of());
        when(applications.findJobIdsByStudentId(studentId)).thenReturn(List.of());
        when(savedJobs.findJobIdsByStudentIdAndJobStatus(studentId, JobStatus.APPROVED)).thenReturn(List.of());

        var context = service.forStudent(studentId);

        assertThat(context.programCode()).isEqualTo("IT");
        assertThat(context.activeTermId()).isNull();
        assertThat(context.preferences()).isEmpty();
    }

    @Test
    void returnsEmptyContextForStudentWithoutProfileOrRoster() {
        when(profiles.findByUserId(studentId)).thenReturn(Optional.empty());
        when(rosters.findFirstByClaimedUserIdOrderByCreatedAtDesc(studentId)).thenReturn(Optional.empty());
        when(skills.findByIdStudentIdAndIsConfirmedTrue(studentId)).thenReturn(List.of());
        when(applications.findJobIdsByStudentId(studentId)).thenReturn(List.of());
        when(savedJobs.findJobIdsByStudentIdAndJobStatus(studentId, JobStatus.APPROVED)).thenReturn(List.of());

        var context = service.forStudent(studentId);

        assertThat(context.programCode()).isNull();
        assertThat(context.activeTermId()).isNull();
        assertThat(context.confirmedSkillIds()).isEmpty();
    }

    private static AcademicProgram program(String code, String name) {
        AcademicProgram program = new AcademicProgram();
        program.setCode(code);
        program.setName(name);
        return program;
    }

    private static InternshipTerm term(TermStatus status) {
        InternshipTerm term = new InternshipTerm();
        term.setId(UUID.randomUUID());
        term.setTermName("HK1 2026-2027");
        term.setStatus(status);
        return term;
    }

    private static StudentRoster roster(InternshipTerm term, AcademicProgram program) {
        StudentRoster roster = new StudentRoster();
        roster.setTerm(term);
        roster.setProgram(program);
        return roster;
    }

    private static StudentSkill skill(String id) {
        SkillTaxonomy taxonomy = new SkillTaxonomy();
        taxonomy.setId(id);
        StudentSkill skill = new StudentSkill();
        skill.setSkill(taxonomy);
        return skill;
    }
}
