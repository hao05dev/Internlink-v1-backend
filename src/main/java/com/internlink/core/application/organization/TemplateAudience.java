package com.internlink.core.application.organization;

import java.util.UUID;

/**
 * Ngữ cảnh của sinh viên mà biểu mẫu sẽ được phát cho: khoa, kỳ, ngành và số tín chỉ thực tập.
 * Mọi trường đều có thể null khi không xác định được.
 */
public record TemplateAudience(
    UUID termId,
    UUID departmentId,
    String programCode,
    Integer internshipCredits
) {
    public static TemplateAudience none() {
        return new TemplateAudience(null, null, null, null);
    }

    public TemplateAudience withProgramCodeIfMissing(String fallbackProgramCode) {
        if (programCode != null || fallbackProgramCode == null || fallbackProgramCode.isBlank()) {
            return this;
        }
        String code = fallbackProgramCode.trim().toUpperCase();
        return "DEFAULT".equals(code) ? this : new TemplateAudience(termId, departmentId, code, internshipCredits);
    }
}
