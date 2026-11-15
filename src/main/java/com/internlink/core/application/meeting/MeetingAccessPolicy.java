package com.internlink.core.application.meeting;

import com.internlink.core.domain.meeting.Meeting;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/** Quy tắc quyền: GVHD chỉ mời sinh viên mình hướng dẫn và chỉ sửa họp do mình tạo. */
@Component
@RequiredArgsConstructor
public class MeetingAccessPolicy {
    private final SupervisedStudentDirectory directory;

    public void requireSupervisedAttendees(UUID lecturerId, Collection<UUID> attendeeIds) {
        Set<UUID> supervised = directory.forLecturer(lecturerId).stream()
                .map(SupervisedStudentDirectory.SupervisedStudent::userId)
                .collect(Collectors.toSet());
        if (!supervised.containsAll(attendeeIds)) {
            throw new ForbiddenException("Chỉ được chọn sinh viên bạn đang hướng dẫn");
        }
    }

    public void requireOrganizer(Meeting meeting, UUID userId) {
        if (!meeting.getOrganizerId().equals(userId)) {
            throw new ForbiddenException("Bạn không phải người tổ chức cuộc họp này");
        }
    }

    public void requireScheduled(Meeting meeting) {
        if (!Meeting.STATUS_SCHEDULED.equals(meeting.getStatus())) {
            throw new BadRequestException("Cuộc họp đã bị hủy, không thể thay đổi");
        }
    }
}
