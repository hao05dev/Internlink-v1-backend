package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.shared.enums.RosterSource;
import com.internlink.core.shared.security.DepartmentScope;

import java.util.UUID;

/**
 * Văn phòng Trường nắm danh sách SV thực tập của cả kỳ (nguồn chuẩn); Khoa chỉ bổ sung SV ngành của khoa mình.
 * Quy tắc khi một dòng nhập trùng MSSV đã có trong kỳ.
 */
public final class RosterImportRules {
    private RosterImportRules() {}

    public enum Action {
        /** SV chưa có trong kỳ: thêm mới. */
        CREATE,
        /** Cập nhật mọi thông tin (ô trống trong tệp không xóa dữ liệu đang có). */
        UPDATE,
        /** Dòng do VP Trường nhập: Khoa chỉ được cập nhật GVHD, giữ nguyên hồ sơ. */
        LECTURER_ONLY,
        /** SV đang thuộc khoa khác: Khoa không được đụng tới. */
        SKIP_OTHER_DEPARTMENT
    }

    /** @param actorDepartmentId null khi người nhập là VP Trường / Admin (phạm vi toàn Trường). */
    public static Action decide(UUID actorDepartmentId, StudentRoster existing) {
        if (existing == null) return Action.CREATE;
        if (actorDepartmentId == null) return Action.UPDATE;
        if (!actorDepartmentId.equals(DepartmentScope.idOf(existing))) return Action.SKIP_OTHER_DEPARTMENT;
        return existing.getSource() == RosterSource.FACULTY ? Action.UPDATE : Action.LECTURER_ONLY;
    }

    public static RosterSource sourceFor(UUID actorDepartmentId) {
        return actorDepartmentId == null ? RosterSource.SCHOOL_OFFICE : RosterSource.FACULTY;
    }
}
