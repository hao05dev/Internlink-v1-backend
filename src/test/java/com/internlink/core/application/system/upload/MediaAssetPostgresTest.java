package com.internlink.core.application.system.upload;

import static org.assertj.core.api.Assertions.*;

import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jdbc.*;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.*;

import java.util.*;

@DataJpaTest(
        showSql = false,
        properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MediaAssetRepository.class, CompanyMediaReferenceRepository.class, UploadTicketRepository.class})
@EnabledIfEnvironmentVariable(named = "INTERNLINK_TEST_PG_URL", matches = ".+")
class MediaAssetPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getenv("INTERNLINK_TEST_PG_URL"),
                schema = "media_test_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(new DriverManagerDataSource(url, "internlink_test", ""))
                .execute("CREATE SCHEMA " + schema);
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + schema);
        properties.add("spring.datasource.username", () -> "internlink_test");
        properties.add("spring.datasource.password", () -> "");
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.schemas", () -> schema);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired JpaJobPositionRepository jobs;
    @Autowired JpaCompanyRepository companies;
    @Autowired JpaInternshipTermRepository terms;
    @Autowired JpaDepartmentRepository departments;
    @Autowired JpaUserRepository users;
    @Autowired MediaAssetRepository assets;
    @Autowired UploadTicketRepository tickets;

    @Test
    void companyLogoIsPublicOnlyWhileReferencedByAVerifiedCompany() {
        var company = companies.findAll().getFirst();
        var owner = users.findAll().getFirst();
        var asset = new MediaAssetRepository.Asset(UUID.randomUUID(), owner.getId(),
                MediaPurpose.COMPANY_LOGO, company.getId(), null,
                "internlink/media/test/" + UUID.randomUUID() + ".png", "logo.png", "image/png", 3, "sum");
        assets.insert(asset);
        assertThat(assets.publicReference(asset)).isFalse();
        company.setLogoUrl(asset.url());
        company.setVerificationStatus(VerificationStatus.VERIFIED);
        companies.saveAndFlush(company);
        assertThat(assets.publicReference(asset)).isTrue();
        company.setVerificationStatus(VerificationStatus.PENDING);
        companies.saveAndFlush(company);
        assertThat(assets.publicReference(asset)).isFalse();
        company.setVerificationStatus(VerificationStatus.VERIFIED);
        company.setLogoUrl(null);
        companies.saveAndFlush(company);
        assertThat(assets.publicReference(asset)).isFalse();
    }

    @Test
    void bannerBecomesPublicOnlyWhileBoundJobIsApprovedAndCannotBePurgedAsAnOrphan() {
        var company = companies.findAll().getFirst();
        var term = terms.findAll().getFirst();
        var owner = users.findAll().getFirst();
        var asset =
                new MediaAssetRepository.Asset(
                        UUID.randomUUID(),
                        owner.getId(),
                        MediaPurpose.JOB_BANNER,
                        company.getId(),
                        null,
                        "internlink/media/test/" + UUID.randomUUID() + ".png",
                        "poster.png",
                        "image/png",
                        3,
                        "sum");
        assets.insert(asset);
        assertThat(assets.publicReference(asset)).isFalse();
        assertThat(tickets.referenced(asset.key())).isTrue();
        var job =
                jobs.saveAndFlush(
                        JobPosition.builder()
                                .company(company)
                                .term(term)
                                .title("Media fixture")
                                .location("Test")
                                .workFormat(WorkFormat.ONSITE)
                                .description("Body")
                                .bannerUrl(asset.url())
                                .status(JobStatus.DRAFT)
                                .build());
        assertThat(assets.publicReference(asset)).isFalse();
        assertThat(assets.departmentReference(asset, UUID.randomUUID())).isFalse();
        job.setStatus(JobStatus.APPROVED);
        jobs.saveAndFlush(job);
        assertThat(assets.publicReference(asset)).isTrue();
        assertThat(jobs.findById(job.getId()).orElseThrow().getBannerUrl()).isEqualTo(asset.url());
        job.setStatus(JobStatus.CLOSED);
        jobs.saveAndFlush(job);
        assertThat(assets.publicReference(asset)).isFalse();
    }

    @Test
    void privatePurposeIsNeverPublicEvenIfSomeoneCopiesItsUrl() {
        var owner = users.findAll().getFirst();
        var asset =
                new MediaAssetRepository.Asset(
                        UUID.randomUUID(),
                        owner.getId(),
                        MediaPurpose.PORTFOLIO_IMAGE,
                        UUID.randomUUID(),
                        "M03",
                        "internlink/private/test/" + UUID.randomUUID() + ".png",
                        "assessment.png",
                        "image/png",
                        3,
                        "sum");
        assets.insert(asset);
        assertThat(assets.publicReference(asset)).isFalse();
        assertThat(assets.get(asset.id()).url()).startsWith("/api/v1/media/");
    }

    @Test
    void postMediaRequiresPublicPublicationAndOriginalAuthorContext() {
        var owner = users.findAll().getFirst();
        var asset =
                new MediaAssetRepository.Asset(
                        UUID.randomUUID(),
                        owner.getId(),
                        MediaPurpose.POST_COVER,
                        owner.getId(),
                        null,
                        "internlink/media/test/" + UUID.randomUUID(),
                        "cover.png",
                        "image/png",
                        3,
                        "sum");
        assets.insert(asset);
        var postId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " career_guide_posts(id,author_user_id,title,slug,category,summary,content,cover_image_url,status,visibility)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                postId,
                owner.getId(),
                "Media test",
                "media-" + postId,
                "GENERAL",
                "Summary",
                "Body",
                asset.url(),
                "DRAFT",
                "PUBLIC");
        assertThat(assets.publicReference(asset)).isFalse();
        jdbc.update("UPDATE career_guide_posts SET status='PUBLISHED' WHERE id=?", postId);
        assertThat(assets.publicReference(asset)).isTrue();
        jdbc.update("UPDATE career_guide_posts SET visibility='FACULTY_ONLY' WHERE id=?", postId);
        assertThat(assets.publicReference(asset)).isFalse();
        jdbc.update(
                "UPDATE career_guide_posts SET visibility='PUBLIC', author_user_id=? WHERE id=?",
                users.findAll().stream()
                        .filter(u -> !u.getId().equals(owner.getId()))
                        .findFirst()
                        .orElseThrow()
                        .getId(),
                postId);
        assertThat(assets.publicReference(asset)).isFalse();
    }
}
