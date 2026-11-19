package com.internlink.core.application.system.notification;

import java.util.UUID;

/**
 * Bộ lọc nhật ký email.
 *
 * @param departmentViewerId null = xem toàn hệ thống (Admin); khác null = chỉ thấy email gửi tới
 *     người cùng khoa với người xem này hoặc email gửi theo phạm vi khoa đó (Khoa).
 */
public record EmailDeliveryFilter(
        String status,
        String scopeType,
        String sourceType,
        String templateKey,
        String recipient,
        UUID departmentViewerId) {}
