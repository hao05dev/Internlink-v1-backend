-- Biểu mẫu theo khoa: chỉ M-TT-04 (phiếu đánh giá của GVHD) khác nhau giữa các khoa,
-- các mẫu còn lại dùng chung toàn Trường.
-- V31 gắn cả 3 bản M-TT-04 vào khoa '...111' (V53 đã đổi thành Khoa CNPM), khiến SV KHMT/MMT
-- không nhận được mẫu của khoa mình. Chuyển lại đúng chủ sở hữu:
--   * bản KHMT  -> Khoa Khoa học máy tính
--   * bản MMT   -> Khoa Mạng máy tính và Truyền thông
--   (gắn theo khoa, không theo ngành: Khoa MMT có cả ngành NE và IA)
--   * bản chung -> mẫu cấp Trường (department_id NULL) cho các khoa còn lại

UPDATE report_template_versions
SET department_id = (SELECT id FROM departments WHERE code = 'KHMT'), program_id = NULL, program_code = 'DEFAULT'
WHERE id = '99999999-9999-9999-9999-999999999901'
  AND EXISTS (SELECT 1 FROM departments WHERE code = 'KHMT');

UPDATE report_template_versions
SET department_id = (SELECT id FROM departments WHERE code = 'MMT'), program_id = NULL, program_code = 'DEFAULT'
WHERE id = '99999999-9999-9999-9999-999999999902'
  AND EXISTS (SELECT 1 FROM departments WHERE code = 'MMT');

UPDATE report_template_versions
SET department_id = NULL, program_id = NULL, program_code = 'DEFAULT'
WHERE id = '99999999-9999-9999-9999-999999999903';

-- Bộ biểu mẫu chính thức 2026 (đã đưa lên R2: <prefix>/templates/official/<file_name>).
UPDATE report_template_versions t
SET file_name = v.file_name,
    file_url = '/api/v1/storage/documents/official/' || v.file_name,
    file_size_bytes = v.size_bytes
FROM (VALUES
    ('99999999-9999-9999-9999-999999999901', 'M-TT-04-Phieu_danh_gia_danh cho GVHD-2026-KHMT.doc', 40448),
    ('99999999-9999-9999-9999-999999999902', 'M-TT-04-Phieu_danh_gia_danh cho GVHD-2026_MMT.doc', 41984),
    ('99999999-9999-9999-9999-999999999903', 'M-TT-04-Phieu_danh_gia_danh cho GVHD-2026.doc', 41984),
    ('99999999-9999-9999-9999-999999999904', 'M-TT-05-Maubaocao.doc', 30720),
    ('99999999-9999-9999-9999-999999999905', '04-Phieu_nhan_SV.doc', 37376),
    ('99999999-9999-9999-9999-999999999906', '04-Phieu_nhan_SV_12tuan.doc', 37376),
    ('99999999-9999-9999-9999-999999999907', 'M-TT-01-Phieugiaoviec_8tuan.doc', 37888),
    ('99999999-9999-9999-9999-999999999908', 'M-TT-01-1-Phieugiaoviec_TTDN_12tuan.doc', 40448),
    ('99999999-9999-9999-9999-999999999909', 'M-TT-02-Phieutheodoi_8tuan.doc', 37888),
    ('99999999-9999-9999-9999-999999999910', 'M-TT-02-1-Phieutheodoi_TTDN_12Tuan.doc', 40960),
    ('99999999-9999-9999-9999-999999999911', 'M-TT-03-pdgia_coquan.doc', 38400)
) AS v(id, file_name, size_bytes)
WHERE t.id = v.id::uuid;
