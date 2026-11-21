package com.internlink.core.presentation.company.dto.response;

import java.util.UUID;

/** Mentor của doanh nghiệp, dùng khi DN chọn người hướng dẫn lúc phát hành offer. */
public record CompanyMentorResponse(UUID id, String fullName, String email) {
}
