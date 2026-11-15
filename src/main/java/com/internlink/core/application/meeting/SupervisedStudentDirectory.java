package com.internlink.core.application.meeting;

import java.util.List;
import java.util.UUID;

/** Danh sách sinh viên mà một giảng viên đang hướng dẫn ở các kỳ chưa đóng. */
public interface SupervisedStudentDirectory {
    record SupervisedStudent(UUID userId, String fullName, String email, String studentCode, String termName) {}

    List<SupervisedStudent> forLecturer(UUID lecturerId);
}
