package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.shared.enums.ReportTemplateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static com.internlink.core.application.organization.TemplateTestFixtures.*;
import static com.internlink.core.shared.enums.ReportTemplateType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateSelectorTest {

    private static final UUID TERM = UUID.randomUUID();

    @Mock private JpaReportTemplateVersionRepository templateRepository;

    private TemplateSelector selector;

    @BeforeEach
    void setUp() {
        selector = new TemplateSelector(templateRepository);
    }

    private TemplateAudience student(UUID dept, String program, Integer credits) {
        return new TemplateAudience(TERM, dept, program, credits);
    }

    private void inTerm(ReportTemplateType type, ReportTemplateVersion... templates) {
        when(templateRepository.findByTermIdAndReportTypeAndIsActiveTrue(TERM, type)).thenReturn(List.of(templates));
    }

    @Test
    void studentOfEachFaculty_getsOwnFacultyForm() {
        ReportTemplateVersion khmt = template(KHMT, FORM_04_RECEPTION_8W, "DEFAULT");
        ReportTemplateVersion mmt = template(MMT, FORM_04_RECEPTION_8W, "DEFAULT");
        inTerm(FORM_04_RECEPTION_8W, mmt, khmt);

        assertThat(selector.select(FORM_04_RECEPTION_8W, student(KHMT.getId(), "CS", 3))).containsSame(khmt);
        assertThat(selector.select(FORM_04_RECEPTION_8W, student(MMT.getId(), "NE", 3))).containsSame(mmt);
    }

    @Test
    void neverReturnsAnotherFacultysForm_fallsBackToSchoolWide() {
        ReportTemplateVersion mmt = template(MMT, M01, "DEFAULT");
        ReportTemplateVersion school = template(null, M01, "DEFAULT");
        inTerm(M01, mmt, school);

        assertThat(selector.select(M01, student(KHMT.getId(), "CS", null))).containsSame(school);
    }

    @Test
    void returnsEmpty_whenOnlyOtherFacultyHasForm() {
        inTerm(M01, template(MMT, M01, "DEFAULT"));
        when(templateRepository.findByReportTypeAndIsActiveTrue(M01)).thenReturn(List.of(template(MMT, M01, "DEFAULT")));

        assertThat(selector.select(M01, student(KHMT.getId(), "CS", null))).isEmpty();
    }

    @Test
    void prefersProgramSpecific_thenFacultyDefault_thenSchoolWide() {
        ReportTemplateVersion school = template(null, M04, "CS");
        ReportTemplateVersion facultyDefault = template(KHMT, M04, "DEFAULT");
        ReportTemplateVersion facultyCs = template(KHMT, M04, "CS");
        inTerm(M04, school, facultyDefault, facultyCs);

        assertThat(selector.select(M04, student(KHMT.getId(), "CS", null))).containsSame(facultyCs);
        assertThat(selector.select(M04, student(KHMT.getId(), "IT", null))).containsSame(facultyDefault);
    }

    @Test
    void otherProgramsForm_isNotGivenToStudentOfKnownProgram() {
        ReportTemplateVersion facultyIs = template(KHMT, M04, "IS");
        ReportTemplateVersion school = template(null, M04, "DEFAULT");
        inTerm(M04, facultyIs, school);

        assertThat(selector.select(M04, student(KHMT.getId(), "CS", null))).containsSame(school);
    }

    @Test
    void fiveCreditStudent_getsTwelveWeekReceptionForm() {
        ReportTemplateVersion twelve = template(KHMT, FORM_04_RECEPTION_12W, "DEFAULT");
        inTerm(FORM_04_RECEPTION_12W, twelve);

        assertThat(selector.select(FORM_04_RECEPTION_8W, student(KHMT.getId(), "CS", 5))).containsSame(twelve);
        verify(templateRepository, never()).findByTermIdAndReportTypeAndIsActiveTrue(TERM, FORM_04_RECEPTION_8W);
    }

    @Test
    void fallsBackToLatestFacultyFormFromOtherTerms_whenTermHasNone() {
        inTerm(M02);
        ReportTemplateVersion old = template(KHMT, M02, "DEFAULT");
        when(templateRepository.findByReportTypeAndIsActiveTrue(M02))
            .thenReturn(List.of(template(MMT, M02, "DEFAULT"), old));

        assertThat(selector.select(M02, student(KHMT.getId(), "CS", null))).containsSame(old);
    }

    @Test
    void picksHighestVersion_amongEquallyRankedForms() {
        ReportTemplateVersion v1 = template(KHMT, M03, "DEFAULT");
        ReportTemplateVersion v2 = template(KHMT, M03, "DEFAULT");
        v2.setVersionNumber(2);
        inTerm(M03, v1, v2);

        assertThat(selector.select(M03, student(KHMT.getId(), "CS", null))).containsSame(v2);
    }

    @Test
    void pickWeekVariantOfSameType_byStudentCredits() {
        ReportTemplateVersion eight = template(null, M01, "DEFAULT");
        eight.setFileName("M-TT-01-Phieugiaoviec_8tuan.doc");
        ReportTemplateVersion twelve = template(null, M01, "DEFAULT");
        twelve.setFileName("M-TT-01-1-Phieugiaoviec_TTDN_12tuan.doc");
        inTerm(M01, twelve, eight);

        assertThat(selector.select(M01, student(KHMT.getId(), "CS", 3))).containsSame(eight);
        assertThat(selector.select(M01, student(KHMT.getId(), "CS", 5))).containsSame(twelve);
    }

    @Test
    void onlyM04DiffersByFaculty_otherFacultiesGetCommonM04() {
        ReportTemplateVersion khmt = template(KHMT, M04, "CS");
        ReportTemplateVersion mmt = template(MMT, M04, "NE");
        ReportTemplateVersion common = template(null, M04, "DEFAULT");
        inTerm(M04, khmt, mmt, common);

        assertThat(selector.select(M04, student(KHMT.getId(), "CS", 3))).containsSame(khmt);
        assertThat(selector.select(M04, student(MMT.getId(), "NE", 3))).containsSame(mmt);
        assertThat(selector.select(M04, student(UUID.randomUUID(), "IT", 3))).containsSame(common);
    }
}
