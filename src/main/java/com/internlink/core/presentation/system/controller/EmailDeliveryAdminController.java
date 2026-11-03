package com.internlink.core.presentation.system.controller;

import com.internlink.core.application.system.notification.EmailDeliveryAdminService;
import com.internlink.core.presentation.system.dto.response.EmailDeliveryResponse;
import com.internlink.core.shared.api.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/deliveries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FACULTY_ADMIN')")
public class EmailDeliveryAdminController {
    private final EmailDeliveryAdminService service;

    public record Controls(boolean paused, int dailyLimit, String reason) {}

    public record Action(String reason, boolean confirmedNotAccepted) {}

    @GetMapping
    public ApiResponse<EmailDeliveryResponse.Page> page(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String scopeType,
            @RequestParam(defaultValue = "") String sourceType,
            @RequestParam(defaultValue = "") String templateKey,
            @RequestParam(defaultValue = "") String recipient,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                service.page(status, scopeType, sourceType, templateKey, recipient, page, size));
    }

    @PutMapping("/controls")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<EmailDeliveryResponse.Controls> controls(@RequestBody Controls request) {
        return ApiResponse.success(
                service.controls(request.paused(), request.dailyLimit(), request.reason()));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable UUID id, @RequestBody Action request) {
        service.retry(id, request.confirmedNotAccepted(), request.reason());
        return ApiResponse.success("Đã xếp hàng gửi lại", null);
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID id, @RequestBody Action request) {
        service.cancel(id, request.reason());
        return ApiResponse.success("Đã hủy lượt gửi", null);
    }
}
