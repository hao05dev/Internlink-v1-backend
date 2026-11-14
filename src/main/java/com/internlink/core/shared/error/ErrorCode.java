package com.internlink.core.shared.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi và thông điệp chuẩn toàn cục của InternLink.
 * Mã lỗi trả về phía client ở định dạng snake_case đồng bộ với AI Service và Frontend.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // === HỆ THỐNG & CHUNG (SYS) ===
    INTERNAL_SERVER_ERROR("sys_internal_error", HttpStatus.INTERNAL_SERVER_ERROR, "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"),
    INVALID_INPUT_DATA("sys_invalid_input", HttpStatus.BAD_REQUEST, "Dữ liệu đầu vào không hợp lệ"),
    RESOURCE_NOT_FOUND("sys_resource_not_found", HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu"),
    OPTIMISTIC_LOCK_CONFLICT("sys_conflict_data", HttpStatus.CONFLICT, "Dữ liệu đã thay đổi hoặc bị trùng lặp. Vui lòng tải lại trang"),
    TOO_MANY_REQUESTS("sys_rate_limit_exceeded", HttpStatus.TOO_MANY_REQUESTS, "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau giây lát"),

    // === XÁC THỰC & PHÂN QUYỀN (AUTH) ===
    UNAUTHORIZED("auth_unauthorized", HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc không hợp lệ"),
    INVALID_CREDENTIALS("auth_invalid_credentials", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không chính xác"),
    ACCOUNT_INACTIVE("auth_account_inactive", HttpStatus.UNAUTHORIZED, "Tài khoản đã bị khóa hoặc chưa được kích hoạt"),
    FORBIDDEN_ACCESS("auth_forbidden", HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    ACCOUNT_LOCKED("auth_account_locked", HttpStatus.LOCKED, "Tài khoản tạm thời bị khóa do đăng nhập sai nhiều lần"),

    // === TÀI LIỆU & TỆP TIN (DOC) ===
    DOCUMENT_NOT_FOUND("doc_not_found", HttpStatus.NOT_FOUND, "Tài liệu không tồn tại trên hệ thống"),
    DOCUMENT_OWNERSHIP_INVALID("doc_ownership_invalid", HttpStatus.FORBIDDEN, "Tài liệu nguồn phải là CV thuộc sở hữu của sinh viên"),
    DOCUMENT_TOO_LARGE("document_too_large", HttpStatus.PAYLOAD_TOO_LARGE, "Dung lượng tệp vượt quá giới hạn 10MB cho phép"),
    DOCUMENT_TEXT_TOO_LARGE("document_text_too_large", HttpStatus.PAYLOAD_TOO_LARGE, "Nội dung văn bản trong tài liệu vượt quá giới hạn xử lý"),
    DOCUMENT_MALWARE_DETECTED("doc_malware_detected", HttpStatus.BAD_REQUEST, "Tệp tin bị chặn do phát hiện mã độc"),
    UNSUPPORTED_DOCUMENT_TYPE("unsupported_document_type", HttpStatus.UNPROCESSABLE_ENTITY, "Định dạng tệp không được hỗ trợ (chỉ nhận PDF và DOCX)"),
    DOCUMENT_UNREADABLE("unreadable_document", HttpStatus.UNPROCESSABLE_ENTITY, "Không thể đọc nội dung tệp tin do tệp bị hỏng"),
    TOO_MANY_PAGES("too_many_pages", HttpStatus.UNPROCESSABLE_ENTITY, "Tài liệu vượt quá số trang cho phép (tối đa 50 trang)"),

    // === AI MICROSERVICE & TRÍCH XUẤT CV (AI) ===
    AI_SERVICE_UNAVAILABLE("ai_unavailable", HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI hiện không phản hồi. Đã tự động kích hoạt chế độ dự phòng"),
    AI_CLOUD_CONSENT_REQUIRED("ai_cloud_consent_required", HttpStatus.FORBIDDEN, "Cần có sự đồng ý của sinh viên để gửi tài liệu lên dịch vụ AI đám mây"),
    AI_OCR_REQUIRED("ocr_required", HttpStatus.UNPROCESSABLE_ENTITY, "Tài liệu PDF scan dạng ảnh cần được nhận dạng ký tự (OCR)"),
    AI_ENCRYPTED_PDF("encrypted_pdf", HttpStatus.UNPROCESSABLE_ENTITY, "Tệp PDF bị đặt mật khẩu bảo vệ, vui lòng gỡ mật khẩu trước khi tải lên"),
    AI_EMPTY_RESPONSE("empty_response", HttpStatus.BAD_GATEWAY, "Dịch vụ AI không trả về dữ liệu phân tích"),
    AI_CV_TEXT_LENGTH_INVALID("ai_cv_text_invalid", HttpStatus.BAD_REQUEST, "Nội dung CV phải có độ dài từ 1 đến 100.000 ký tự"),
    AI_RETRY_NOT_ALLOWED("ai_retry_not_allowed", HttpStatus.BAD_REQUEST, "Chỉ có thể xử lý lại các lượt trích xuất CV bị lỗi có sinh viên nguồn"),
    AI_RUN_ALREADY_REPROCESSED("ai_run_already_reprocessed", HttpStatus.BAD_REQUEST, "Lượt xử lý này đã được chạy lại trước đó"),

    // === TUYỂN DỤNG & ỨNG TUYỂN (JOB) ===
    JOB_POSITION_CLOSED("job_closed", HttpStatus.BAD_REQUEST, "Vị trí tuyển dụng đã đóng hoặc hết hạn tiếp nhận hồ sơ"),
    ALREADY_APPLIED("job_already_applied", HttpStatus.CONFLICT, "Sinh viên đã nộp hồ sơ ứng tuyển vào vị trí này"),
    JOB_APPLY_EXTERNAL_ONLY("job_apply_external_only", HttpStatus.BAD_REQUEST, "Tin này nhận hồ sơ qua email/link của doanh nghiệp, không nộp trong hệ thống"),
    TERM_NOT_ACTIVE("term_not_active", HttpStatus.BAD_REQUEST, "Kỳ thực tập hiện tại chưa mở hoặc đã kết thúc giai đoạn ứng tuyển");

    private final String code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;
}
