package com.internlink.core.application.organization.impl;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipDurationRule;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.shared.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

/** Kiểm tra và ghi một dòng nhập danh sách SV vào bản ghi roster. Ô trống trong tệp không xóa dữ liệu đang có. */
final class RosterRowWriter {
    private static final BigDecimal MAX_GPA = new BigDecimal("4.0");

    private RosterRowWriter() {}

    static void validate(StudentRosterImportItem item) {
        if (item.getProgramId() == null
                || item.getStudentCode() == null || item.getStudentCode().isBlank()
                || item.getFullName() == null || item.getFullName().isBlank()
                || item.getOfficialEmail() == null || !item.getOfficialEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                || item.getAcademicYear() == null || item.getAcademicYear().isBlank()) {
            throw new BadRequestException("Dòng roster thiếu MSSV, họ tên, email, khóa hoặc ngành học");
        }
        if (item.getInternshipCourseCode() == null || item.getInternshipCourseCode().isBlank()) {
            throw new BadRequestException("Cần mã học phần thực tập theo chương trình đào tạo");
        }
        if (item.getGpa() == null
                || item.getGpa().compareTo(BigDecimal.ZERO) < 0
                || item.getGpa().compareTo(MAX_GPA) > 0) {
            throw new BadRequestException("Điểm GPA của sinh viên " + item.getStudentCode() + " phải nằm trong khoảng 0.0 - 4.0");
        }
    }

    static String studentCode(StudentRosterImportItem item) {
        return sanitize(item.getStudentCode()).toUpperCase();
    }

    /** Ghi hồ sơ học vụ của SV (ngành, email, họ tên, khóa, học phần, GPA, lớp, tín chỉ, SĐT). */
    static void writeProfile(StudentRoster roster, StudentRosterImportItem item, AcademicProgram program) {
        roster.setProgram(program);
        roster.setOfficialEmail(sanitize(item.getOfficialEmail()).toLowerCase());
        roster.setFullName(sanitize(item.getFullName()));
        roster.setAcademicYear(sanitize(item.getAcademicYear()));
        roster.setInternshipCourseCode(sanitize(item.getInternshipCourseCode()).toUpperCase());
        roster.setGpa(item.getGpa());
        if (hasText(item.getClassCode())) {
            roster.setClassCode(sanitize(item.getClassCode()));
        }
        if (item.getInternshipCredits() != null) {
            InternshipDurationRule.requireValidCredits(item.getInternshipCredits());
            roster.setInternshipCredits(item.getInternshipCredits());
        }
        if (hasText(item.getPhoneNumber())) {
            roster.setPhoneNumber(sanitize(item.getPhoneNumber()));
        }
    }

    /** Ghi GVHD học phần nếu dòng có; trả về true khi có thay đổi. */
    static boolean writeLecturer(StudentRoster roster, StudentRosterImportItem item,
                                 Function<String, Optional<User>> userByEmail) {
        boolean changed = false;
        if (hasText(item.getLecturerCode())) {
            roster.setLecturerCode(sanitize(item.getLecturerCode()));
            changed = true;
        }
        if (hasText(item.getLecturerName())) {
            roster.setLecturerName(sanitize(item.getLecturerName()));
            changed = true;
        }
        if (hasText(item.getLecturerEmail())) {
            String email = sanitize(item.getLecturerEmail()).toLowerCase();
            roster.setLecturerEmail(email);
            userByEmail.apply(email).ifPresent(roster::setAssignedLecturer);
            changed = true;
        }
        return changed;
    }

    /** Bỏ ký tự đầu dòng có thể bị Excel hiểu là công thức. */
    static String sanitize(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        while (trimmed.startsWith("=") || trimmed.startsWith("+") || trimmed.startsWith("-")
                || trimmed.startsWith("@") || trimmed.startsWith("\t") || trimmed.startsWith("\r")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
