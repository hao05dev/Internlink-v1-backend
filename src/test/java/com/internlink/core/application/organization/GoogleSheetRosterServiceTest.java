package com.internlink.core.application.organization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.infrastructure.integration.google.GoogleWorkspaceClient;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.security.SecurityGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleSheetRosterServiceTest {
    @Mock GoogleWorkspaceClient google;
    @Mock JpaAcademicProgramRepository programs;
    @Mock StudentRosterService rosterService;
    @Mock JpaInternshipTermRepository terms;
    @Mock JpaUserRepository users;
    @Mock SecurityGuard securityGuard;

    @Test
    void mapsSharedSheetRowsToRosterImportItems() throws Exception {
        UUID termId = UUID.randomUUID();
        Department department = Department.builder().code("CIT").name("CNTT").build();
        department.setId(UUID.randomUUID());
        InternshipTerm term = InternshipTerm.builder().code("T1").build();
        term.setId(termId);
        User actor = User.builder().email("faculty@ctu.edu.vn").fullName("Faculty")
            .role(UserRole.FACULTY_ADMIN).department(department).build();
        actor.setId(UUID.randomUUID());
        AcademicProgram program = AcademicProgram.builder().department(department).code("SE").name("Software").build();
        program.setId(UUID.randomUUID());
        when(terms.findById(termId)).thenReturn(Optional.of(term));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(actor));
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(programs.findByCode("SE")).thenReturn(Optional.of(program));
        when(google.json(eq("GET"), any(URI.class), eq(GoogleWorkspaceClient.SHEETS_READ), isNull(), isNull(), isNull()))
            .thenReturn(new ObjectMapper().readTree("{\"values\":[[\"studentCode\",\"fullName\",\"officialEmail\",\"programCode\",\"academicYear\",\"internshipCourseCode\",\"gpa\"],[\"b2110940\",\"Lê Hoàng Nam\",\"b2110940@student.ctu.edu.vn\",\"se\",\"K47\",\"CT444\",\"3,45\"]]}"));
        when(rosterService.importRosterList(eq(termId), anyList())).thenReturn(List.of());

        new GoogleSheetRosterService(google, programs, rosterService, terms, users, securityGuard)
            .importSheet(termId, "12345678901234567890", "Sheet1!A:F");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StudentRosterImportItem>> items = ArgumentCaptor.forClass(List.class);
        verify(rosterService).importRosterList(eq(termId), items.capture());
        assertThat(items.getValue()).hasSize(1);
        assertThat(items.getValue().get(0).getStudentCode()).isEqualTo("B2110940");
        assertThat(items.getValue().get(0).getProgramId()).isEqualTo(program.getId());
        assertThat(items.getValue().get(0).getGpa()).isEqualByComparingTo("3.45");
    }
}
