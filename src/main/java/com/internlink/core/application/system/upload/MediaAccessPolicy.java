package com.internlink.core.application.system.upload;

import com.internlink.core.application.portfolio.PortfolioAccess;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.PortfolioForm;
import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class MediaAccessPolicy {
    private final SecurityGuard guard;
    private final JpaUserRepository users;
    private final JpaCompanyRepository companies;
    private final JpaInternshipPlacementRepository placements;
    private final JpaPortfolioFormRepository forms;
    private final MediaAssetRepository assets;

    public User actor() {
        return users.findById(guard.currentUser().getId())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new ForbiddenException("Tài khoản không hoạt động"));
    }

    public User optionalActor() {
        try {
            return actor();
        } catch (ForbiddenException ex) {
            return null;
        }
    }

    public UUID requireUpload(User user, MediaPurpose purpose, UUID context, String scope) {
        if (purpose.job() || purpose.company()) {
            if (context == null || !companies.existsById(context))
                throw new BadRequestException("Chọn doanh nghiệp trước khi tải ảnh/tệp");
            if (purpose.job()) {
                ResourceAuthorization.require(
                        ResourceAuthorization.actsForSchool(user)
                                || ResourceAuthorization.representsCompany(user, context));
            } else {
                ResourceAuthorization.require(
                        ResourceAuthorization.isAdmin(user)
                                || ResourceAuthorization.representsCompany(user, context));
            }
            return context;
        }
        if (purpose.post()) {
            ResourceAuthorization.require(ResourceAuthorization.actsForSchool(user));
            if (context == null || context.equals(user.getId())) return user.getId();
            var author =
                    users.findById(context)
                            .orElseThrow(
                                    () -> new ResourceNotFoundException("Author", "id", context));
            ResourceAuthorization.require(
                    ResourceAuthorization.isAdmin(user)
                            || user.getId().equals(author.getId())
                            || ResourceAuthorization.isSchoolOffice(author));
            return context;
        }
        if (context == null
                || scope == null
                || !Set.of("DAILY", "M01", "M02", "M03", "M04", "M05").contains(scope))
            throw new BadRequestException("Thiếu phạm vi hồ sơ cho ảnh minh chứng");
        var p =
                placements
                        .findById(context)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Placement", "id", context));
        TermGuard.requireNotClosed(p.getTerm());
        if ("DAILY".equals(scope))
            ResourceAuthorization.require(
                    PortfolioAccess.student(user, p) && p.getStatus() == PlacementStatus.ACTIVE);
        else {
            var form =
                    forms.findByPlacementIdAndKind(context, scope)
                            .orElseGet(() -> newForm(p, scope));
            ResourceAuthorization.require(PortfolioAccess.edit(user, form) && !form.isPublished());
        }
        return context;
    }

    private static PortfolioForm newForm(
            com.internlink.core.domain.placement.InternshipPlacement placement, String kind) {
        var form = new PortfolioForm();
        form.setPlacement(placement);
        form.setKind(kind);
        return form;
    }

    public boolean canRead(User user, MediaAssetRepository.Asset asset) {
        if (asset.purpose().privateImage()) {
            if (user == null) return false;
            var placement = placements.findById(asset.context()).orElse(null);
            if (placement == null) return false;
            if ("DAILY".equals(asset.scope())) return PortfolioAccess.read(user, placement);
            return forms.findByPlacementIdAndKind(asset.context(), asset.scope())
                    .map(f -> PortfolioAccess.content(user, f))
                    .orElseGet(() -> PortfolioAccess.edit(user, newForm(placement, asset.scope())));
        }
        if (assets.publicReference(asset)) return true;
        if (user == null) return false;
        if (user.getId().equals(asset.owner()) || ResourceAuthorization.isAdmin(user)) return true;
        if (asset.purpose().job() && (user.getRole() == UserRole.SCHOOL_OFFICE || user.getRole() == UserRole.FACULTY_ADMIN))
            return true;
        if ((asset.purpose().job() || asset.purpose().company()) && ResourceAuthorization.representsCompany(user, asset.context()))
            return true;
        if (asset.purpose().company()) return false;
        if (asset.purpose().post() && ResourceAuthorization.isSchoolOffice(user)) return true;
        return user.getRole() == UserRole.FACULTY_ADMIN
                && user.getDepartment() != null
                && assets.departmentReference(asset, user.getDepartment().getId());
    }
}
