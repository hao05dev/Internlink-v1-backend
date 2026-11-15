package com.internlink.core.presentation.announcement.controller;

import com.internlink.core.application.announcement.AnnouncementDtos.*;
import com.internlink.core.application.announcement.AnnouncementService;
import com.internlink.core.shared.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Thông báo GVHD gửi sinh viên: GVHD gửi và xem lịch sử, sinh viên xem thông báo đã nhận. */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService service;

    @PostMapping
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<AnnouncementView> send(@Valid @RequestBody AnnouncementRequest request) {
        return ApiResponse.success(service.send(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('LECTURER')")
    public ApiResponse<List<AnnouncementView>> sent() {
        return ApiResponse.success(service.listSent());
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<AnnouncementView>> mine() {
        return ApiResponse.success(service.listMine());
    }
}
