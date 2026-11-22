-- M-TT-05 (mẫu báo cáo) giống nhau cho mọi khoa, nhưng V31 gắn vào khoa '...111' (nay là Khoa CNPM),
-- nên SV các khoa khác không nhận được bản đã treo. Chuyển thành mẫu cấp Trường.
UPDATE report_template_versions
SET department_id = NULL, program_id = NULL, program_code = 'DEFAULT'
WHERE id = '99999999-9999-9999-9999-999999999904';
