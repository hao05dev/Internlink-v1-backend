package com.internlink.core.presentation.meeting.controller;

import com.internlink.core.application.meeting.MeetingDtos.*;
import com.internlink.core.application.meeting.MeetingService;
import com.internlink.core.application.meeting.SupervisedStudentDirectory.SupervisedStudent;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Cuộc họp Google Meet: GVHD tổ chức, sinh viên xem các buổi mình được mời. */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {
    private final MeetingService service;

    @GetMapping
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<List<MeetingView>> organized(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ApiResponse.success(service.listOrganized(from, to));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<MeetingView>> mine(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return ApiResponse.success(service.listMine(from, to));
    }

    @GetMapping("/invitable-students")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<List<SupervisedStudent>> invitableStudents() {
        return ApiResponse.success(service.supervisedStudents());
    }

    @PostMapping
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<MeetingView> create(@Valid @RequestBody MeetingRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<MeetingView> update(@PathVariable UUID id, @Valid @RequestBody MeetingRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<MeetingView> cancel(@PathVariable UUID id, @Valid @RequestBody(required = false) CancelRequest request) {
        return ApiResponse.success(service.cancel(id, request));
    }

    @PutMapping("/{id}/attendance")
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<MeetingView> attendance(@PathVariable UUID id, @Valid @RequestBody AttendanceRequest request) {
        return ApiResponse.success(service.recordAttendance(id, request));
    }
}
