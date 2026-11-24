-- Văn phòng Trường nắm danh sách SV thực tập của cả kỳ; Khoa chỉ bổ sung SV ngành của khoa mình.
-- Ghi lại nguồn của từng dòng để hai bên thấy dòng nào do Khoa bổ sung. Dữ liệu cũ tính là VP Trường nhập.
ALTER TABLE student_rosters
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SCHOOL_OFFICE'
        CHECK (source IN ('SCHOOL_OFFICE', 'FACULTY')),
    ADD COLUMN added_by_id UUID REFERENCES users(id) ON DELETE SET NULL;
