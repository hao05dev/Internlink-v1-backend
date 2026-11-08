package com.internlink.core.presentation.system.controller;

import com.internlink.core.application.system.upload.*;
import com.internlink.core.shared.api.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MediaAssetController {
    private final MediaAssetService service;

    @PostMapping(value = "/api/v1/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MediaAssetService.Uploaded> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam MediaPurpose purpose,
            @RequestParam(required = false) UUID contextId,
            @RequestParam(required = false) String scope)
            throws IOException {
        return ApiResponse.success(service.upload(file, purpose, contextId, scope));
    }

    @DeleteMapping("/api/v1/media/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/media/limits")
    public ApiResponse<MediaLimits> limits(@RequestParam MediaPurpose purpose) {
        return ApiResponse.success(MediaLimits.of(purpose));
    }

    @GetMapping("/api/v1/media/{id}")
    public ResponseEntity<Resource> privateFile(@PathVariable UUID id) {
        return response(service.download(id, false));
    }

    @GetMapping("/api/v1/public/media/{id}")
    public ResponseEntity<Resource> publicFile(@PathVariable UUID id) {
        return response(service.download(id, true));
    }

    private ResponseEntity<Resource> response(MediaAssetService.Download file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mime()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        (file.mime().startsWith("image/")
                                        ? ContentDisposition.inline()
                                        : ContentDisposition.attachment())
                                .filename(file.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(file.resource());
    }
}
