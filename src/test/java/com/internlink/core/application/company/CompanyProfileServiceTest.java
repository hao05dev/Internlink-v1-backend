package com.internlink.core.application.company;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.internlink.core.application.company.impl.*;
import com.internlink.core.application.system.upload.MediaReferencePolicy;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaCompanyRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.UserRole;
import java.util.List;
import com.internlink.core.presentation.company.dto.request.CompanyProfileRequest;
import com.internlink.core.shared.enums.VerificationStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyProfileServiceTest {
    @Test void savesSanitizedProfileWithoutChangingLegalOrVerificationFields() {
        var company = Company.builder().companyName("Acme").taxCode("123")
                .address(Map.of("city", "Can Tho")).verificationStatus(VerificationStatus.VERIFIED).build();
        company.setId(UUID.randomUUID());
        var access = mock(CompanyAccessPolicy.class);
        var repository = mock(JpaCompanyRepository.class);
        var media = mock(MediaReferencePolicy.class);
        when(access.ownCompany()).thenReturn(company);
        when(repository.save(company)).thenReturn(company);
        var service = new CompanyProfileServiceImpl(access, repository, new CompanyProfileMapper(), new CompanyPublicMapper(), media, mock(JpaUserRepository.class));
        var request = new CompanyProfileRequest();
        request.setDescription("<h2>About</h2><script>alert(1)</script><p>Work with us</p>");
        request.setOfficeAddress("  New office  ");
        var response = service.updateOwnProfile(request);
        assertThat(response.description()).contains("<h2>About</h2>").doesNotContain("script", "alert");
        assertThat(response.officeAddress()).isEqualTo("New office");
        assertThat(company.getTaxCode()).isEqualTo("123");
        assertThat(company.getAddress()).containsEntry("city", "Can Tho");
        assertThat(company.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        verify(media).company(company.getId(), null, null);
    }

    @Test void rejectsMapWithoutOfficeAddress() {
        var request = new CompanyProfileRequest();
        request.setMapsUrl("https://maps.app.goo.gl/abc");
        assertThatThrownBy(() -> new CompanyProfileMapper().apply(new Company(), request))
                .hasMessageContaining("địa chỉ");
    }

    @Test void listsOnlyActiveMentorsOfOwnCompanySortedByName() {
        var company = Company.builder().companyName("Acme").build();
        company.setId(UUID.randomUUID());
        var access = mock(CompanyAccessPolicy.class);
        var users = mock(JpaUserRepository.class);
        when(access.ownCompany()).thenReturn(company);
        var binh = User.builder().fullName("Trần Bình").email("binh@acme.vn").isActive(true).build();
        var an = User.builder().fullName("Lê An").email("an@acme.vn").isActive(true).build();
        var off = User.builder().fullName("Nghỉ Việc").email("off@acme.vn").isActive(false).build();
        when(users.findByCompanyIdAndRole(company.getId(), UserRole.COMPANY_MENTOR)).thenReturn(List.of(binh, off, an));
        var service = new CompanyProfileServiceImpl(access, mock(JpaCompanyRepository.class), new CompanyProfileMapper(),
                new CompanyPublicMapper(), mock(MediaReferencePolicy.class), users);
        assertThat(service.getOwnMentors()).extracting(m -> m.email()).containsExactly("an@acme.vn", "binh@acme.vn");
    }
}
