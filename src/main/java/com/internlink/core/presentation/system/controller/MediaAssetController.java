package com.internlink.core.presentation.system.controller;

import com.internlink.core.application.system.upload.*;
import com.internlink.core.shared.api.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
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
    public ResponseEntity<?> privateFile(@PathVariable UUID id, HttpServletRequest request) {
        return response(id, false, null, request);
    }

    @GetMapping("/api/v1/public/media/{id}")
    public ResponseEntity<?> publicFile(@PathVariable UUID id, HttpServletRequest request) {
        return response(id, true, null, request);
    }

    @GetMapping("/api/v1/media/{id}/preview")
    public ResponseEntity<?> privatePreview(@PathVariable UUID id, HttpServletRequest request) {
        return response(id, false, true, request);
    }

    @GetMapping("/api/v1/media/{id}/download")
    public ResponseEntity<?> privateDownload(@PathVariable UUID id, HttpServletRequest request) {
        return response(id, false, false, request);
    }

    @GetMapping("/api/v1/public/media/{id}/preview")
    public ResponseEntity<?> publicPreview(@PathVariable UUID id, HttpServletRequest request) {
        return response(id, true, true, request);
    }

    @GetMapping("/api/v1/public/media/{id}/download")
    public ResponseEntity<?> publicDownload(@PathVariable UUID id, HttpServletRequest request) {
        return response(id, true, false, request);
    }

    private ResponseEntity<?> response(
            UUID id, boolean publicEndpoint, Boolean inline, HttpServletRequest request) {
        var file = service.metadata(id, publicEndpoint);
        boolean resolvedInline = inline != null ? inline : file.mime().startsWith("image/");
        String etag = "\"" + file.checksum() + "\"";
        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl(publicEndpoint, Boolean.TRUE.equals(inline)))
                    .build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.mime()));
        headers.setCacheControl(cacheControl(publicEndpoint, Boolean.TRUE.equals(inline)));
        headers.setETag(etag);
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentDisposition(
                (resolvedInline ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(file.name(), StandardCharsets.UTF_8)
                        .build());

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader == null || rangeHeader.isBlank())
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(file.size())
                    .body(service.content(file));

        try {
            List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
            if (ranges.size() != 1) return rangeNotSatisfiable(file.size());
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(file.size());
            long end = range.getRangeEnd(file.size());
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .contentLength(end - start + 1)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + file.size())
                    .body(service.content(file, start, end));
        } catch (IllegalArgumentException ex) {
            return rangeNotSatisfiable(file.size());
        }
    }

    private ResponseEntity<Void> rangeNotSatisfiable(long size) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                .build();
    }

    private CacheControl cacheControl(boolean publicEndpoint, boolean inline) {
        return publicEndpoint && inline
                ? CacheControl.maxAge(Duration.ofHours(1)).cachePublic()
                : CacheControl.noStore();
    }
}
