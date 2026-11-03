package com.internlink.core.application.auth.impl;

import com.internlink.core.application.auth.CompanyRegistrationService;
import com.internlink.core.application.auth.RefreshTokenService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.infrastructure.persistence.jpa.JpaCompanyRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CookieUtils;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.infrastructure.security.JwtUtil;
import com.internlink.core.presentation.auth.dto.request.RegisterCompanyRequest;
import com.internlink.core.presentation.auth.dto.response.AuthResponse;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.enums.VerificationStatus;
import com.internlink.core.shared.exception.BadRequestException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyRegistrationServiceImpl implements CompanyRegistrationService {

    private final JpaUserRepository userRepository;
    private final JpaCompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;
    private final CookieUtils cookieUtils;

    @Autowired(required = false)
    private RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public AuthResponse registerCompany(RegisterCompanyRequest request, HttpServletResponse response) {
        com.internlink.core.application.auth.PasswordPolicy.validate(request.getPassword());
        String cleanEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(cleanEmail)) {
            throw new BadRequestException("Email '" + cleanEmail + "' đã được sử dụng trong hệ thống");
        }

        String cleanTaxCode = request.getTaxCode().trim();
        if (companyRepository.existsByTaxCode(cleanTaxCode)) {
            throw new BadRequestException("Mã số thuế '" + cleanTaxCode + "' đã tồn tại trong hệ thống");
        }

        Company company = Company.builder()
            .companyName(request.getCompanyName().trim())
            .taxCode(cleanTaxCode)
            .industry(request.getIndustry())
            .website(request.getWebsite())
            .address(request.getAddress().toMap())
            .verificationStatus(VerificationStatus.PENDING)
            .verificationDetail(Map.of("registeredBy", cleanEmail, "registeredAt", OffsetDateTime.now().toString()))
            .build();
        Company savedCompany = companyRepository.save(company);

        User user = User.builder()
            .email(cleanEmail)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .fullName(request.getFullName().trim())
            .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
            .role(UserRole.COMPANY_REP)
            .company(savedCompany)
            .mustChangePassword(false)
            .isActive(true)
            .build();
        User savedUser = userRepository.save(user);

        for (UserRole role : List.of(UserRole.SCHOOL_OFFICE, UserRole.ADMIN)) {
            userRepository.findByRole(role).forEach(admin ->
                notificationService.sendNotification(
                    admin.getId(),
                    "COMPANY_REGISTERED",
                    "Doanh nghiệp mới chờ thẩm định",
                    savedCompany.getCompanyName() + " đã đăng ký tài khoản tham gia hệ thống.",
                    role == UserRole.ADMIN ? "/admin/companies" : "/school-office/companies"
                )
            );
        }

        CustomUserDetail principal = CustomUserDetail.create(savedUser);
        String token = jwtUtil.generateToken(principal);
        ResponseCookie cookie = cookieUtils.createAccessTokenCookie(token, jwtUtil.getExpirationMs());
        com.internlink.core.infrastructure.security.AuthCookieWriter.add(response, HttpHeaders.SET_COOKIE, cookie.toString());

        if (refreshTokenService != null) {
            refreshTokenService.createSessionAndToken(savedUser, null, response);
        }

        return AuthResponse.builder()
            .userId(savedUser.getId())
            .email(savedUser.getEmail())
            .fullName(savedUser.getFullName())
            .role(savedUser.getRole())
            .mustChangePassword(savedUser.getMustChangePassword())
            .build();
    }
}
