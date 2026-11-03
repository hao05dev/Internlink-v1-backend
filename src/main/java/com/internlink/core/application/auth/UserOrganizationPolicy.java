package com.internlink.core.application.auth;

import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaCompanyRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaDepartmentRepository;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserOrganizationPolicy {
    private final JpaDepartmentRepository departments;
    private final JpaCompanyRepository companies;
    public void assign(User user, UserRole role, UUID departmentId, UUID companyId) {
        if (role == UserRole.FACULTY_ADMIN || role == UserRole.LECTURER || role == UserRole.STUDENT) {
            if (departmentId == null) throw new BadRequestException("Vai trò này cần khoa trực thuộc");
            user.setDepartment(departments.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department", "id", departmentId)));
            user.setCompany(null);
        } else if (role == UserRole.COMPANY_REP || role == UserRole.COMPANY_MENTOR) {
            if (companyId == null) throw new BadRequestException("Vai trò này cần doanh nghiệp trực thuộc");
            user.setCompany(companies.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company", "id", companyId)));
            user.setDepartment(null);
        } else {
            // ADMIN và Văn phòng Trường CNTT-TT hoạt động ở cấp Trường, không gắn với khoa nào.
            user.setDepartment(null);
            user.setCompany(null);
        }
    }
}
