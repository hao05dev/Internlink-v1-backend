package com.internlink.core.application.system.upload;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.*;
import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.Test;

import java.util.*;

class MediaAccessPolicyTest {
    @Test
    void adminCanUploadForOriginalPostAuthorButFacultyCannotCrossDepartments() {
        var users = mock(JpaUserRepository.class);
        var policy =
                new MediaAccessPolicy(
                        mock(SecurityGuard.class),
                        users,
                        mock(JpaCompanyRepository.class),
                        mock(JpaInternshipPlacementRepository.class),
                        mock(JpaPortfolioFormRepository.class),
                        mock(MediaAssetRepository.class));
        var author = User.builder().role(UserRole.FACULTY_ADMIN).build();
        author.setId(UUID.randomUUID());
        when(users.findById(author.getId())).thenReturn(Optional.of(author));
        var admin = User.builder().role(UserRole.ADMIN).build();
        admin.setId(UUID.randomUUID());
        assertThat(policy.requireUpload(admin, MediaPurpose.POST_COVER, author.getId(), null))
                .isEqualTo(author.getId());
        var faculty = User.builder().role(UserRole.FACULTY_ADMIN).build();
        faculty.setId(UUID.randomUUID());
        assertThatThrownBy(
                        () ->
                                policy.requireUpload(
                                        faculty, MediaPurpose.POST_COVER, author.getId(), null))
                .isInstanceOf(com.internlink.core.shared.exception.ForbiddenException.class);
        assertThatThrownBy(() -> policy.requireUpload(faculty, MediaPurpose.POST_COVER, null, null))
                .isInstanceOf(com.internlink.core.shared.exception.ForbiddenException.class);

        var blogManager = User.builder().role(UserRole.SCHOOL_OFFICE).build();
        blogManager.setId(UUID.randomUUID());
        assertThat(policy.requireUpload(blogManager, MediaPurpose.POST_COVER, null, null))
                .isEqualTo(blogManager.getId());
    }

    @Test
    void unpublishedAssessmentImageIsHiddenFromStudentEvenIfUrlIsKnown() {
        var placements = mock(JpaInternshipPlacementRepository.class);
        var forms = mock(JpaPortfolioFormRepository.class);
        var assets = mock(MediaAssetRepository.class);
        var policy =
                new MediaAccessPolicy(
                        mock(SecurityGuard.class),
                        mock(JpaUserRepository.class),
                        mock(JpaCompanyRepository.class),
                        placements,
                        forms,
                        assets);
        var student = User.builder().role(UserRole.STUDENT).build();
        student.setId(UUID.randomUUID());
        var mentor = User.builder().role(UserRole.COMPANY_MENTOR).build();
        mentor.setId(UUID.randomUUID());
        var p = InternshipPlacement.builder().student(student).mentor(mentor).build();
        p.setId(UUID.randomUUID());
        var form = new PortfolioForm();
        form.setPlacement(p);
        form.setKind("M03");
        form.setPublished(false);
        var asset =
                new MediaAssetRepository.Asset(
                        UUID.randomUUID(),
                        mentor.getId(),
                        MediaPurpose.PORTFOLIO_IMAGE,
                        p.getId(),
                        "M03",
                        "key",
                        "a.png",
                        "image/png",
                        1,
                        "sum");
        when(placements.findById(p.getId())).thenReturn(Optional.of(p));
        when(forms.findByPlacementIdAndKind(p.getId(), "M03")).thenReturn(Optional.of(form));
        assertThat(policy.canRead(student, asset)).isFalse();
        assertThat(policy.canRead(mentor, asset)).isTrue();
        assertThat(policy.canRead(null, asset)).isFalse();
        form.setPublished(true);
        assertThat(policy.canRead(student, asset)).isTrue();
    }
}
