package com.internlink.core.shared.enums;

/**
 * Cách sinh viên nộp hồ sơ cho một tin tuyển dụng.
 * IN_APP: nộp trong InternLink, DN đối tác xử lý. EXTERNAL: nộp qua email/link/hướng dẫn của DN
 * (tin Trường đăng lại cho DN không có tài khoản); InternLink không tạo đơn ứng tuyển.
 */
public enum ApplicationMethod {
    IN_APP,
    EXTERNAL
}
