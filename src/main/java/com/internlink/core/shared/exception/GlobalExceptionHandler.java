package com.internlink.core.shared.exception;

import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.error.AppException;
import com.internlink.core.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bộ xử lý ngoại lệ tập trung toàn cục (Global Exception Handler).
 * Đồng bộ hóa mã lỗi error_code định dạng snake_case trả về cho client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode code = ex.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error("sys_response_status", ex.getReason()));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String constraintName = extractConstraintName(ex);
        log.warn("Data integrity violation: constraint=[{}], details={}", constraintName, ex.getMessage());

        if (constraintName.contains("uq_job_applications_job_student")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ErrorCode.ALREADY_APPLIED, "Bạn đã nộp hồ sơ ứng tuyển vào vị trí này rồi."));
        }
        if (constraintName.contains("uq_placement_student_term_live")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "Sinh viên đã có một vị trí thực tập đang hoạt động trong kỳ này."));
        }
        if (constraintName.contains("uq_interviews_application_round")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "Vòng phỏng vấn này đã tồn tại trong hồ sơ."));
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, "Dữ liệu không hợp lệ hoặc bị xung đột ràng buộc hệ thống. Vui lòng thử lại."));
    }

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED, ex.getMessage()));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccountLocked(AccountLockedException ex) {
        var responseBuilder = ResponseEntity.status(HttpStatus.LOCKED);
        if (ex.getSecondsRemaining() > 0) {
            responseBuilder.header("Retry-After", String.valueOf(ex.getSecondsRemaining()));
        }
        return responseBuilder.body(ApiResponse.error(ErrorCode.ACCOUNT_LOCKED, ex.getMessage()));
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ApiResponse<Void>> handleForbidden(Exception ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN_ACCESS, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .errorCode(ErrorCode.INVALID_INPUT_DATA.getCode())
                        .message("Dữ liệu đầu vào không hợp lệ")
                        .data(errors)
                        .build());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Tham số '%s' có giá trị không đúng định dạng", ex.getName());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA, msg));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        String msg = String.format("Thiếu tham số bắt buộc: %s", ex.getParameterName());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA, msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedInput(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA,
                "Dữ liệu gửi lên không đúng định dạng hoặc thiếu trường bắt buộc"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.DOCUMENT_TOO_LARGE, "Dung lượng tệp tải lên vượt quá giới hạn cho phép"));
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(org.springframework.web.multipart.support.MissingServletRequestPartException ex) {
        String msg = String.format("Thiếu phần tệp tải lên bắt buộc: %s", ex.getRequestPartName());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA, msg));
    }

    @ExceptionHandler(org.springframework.web.multipart.MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(org.springframework.web.multipart.MultipartException ex) {
        log.warn("Lỗi xử lý multipart: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT_DATA, "Yêu cầu tải lên không đúng định dạng multipart/form-data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletRequest req) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.error("[{}] Unhandled {} {}: {}", traceId, req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi hệ thống (mã tham chiếu: " + traceId + ")"));
    }

    private String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                return cve.getConstraintName() != null ? cve.getConstraintName().toLowerCase() : "";
            }
            cause = cause.getCause();
        }
        return ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
    }
}
