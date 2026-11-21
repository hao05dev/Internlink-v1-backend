-- Thay ảnh Unsplash và tệp đính kèm giả (example.com) của 5 bài cẩm nang seed ở V14
-- bằng tệp thật lưu trên R2 (sinh bởi scripts/demo_data/build_seed_post_media.py).
-- Chỉ cập nhật bài còn giữ dữ liệu seed gốc để không ghi đè chỉnh sửa của người dùng.

INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000001', '10000000-0000-0000-0000-000000000002', 'POST_IMAGE', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/post-seed-job-fair-2026.jpg', 'post-seed-job-fair-2026.jpg', 'image/jpeg', 172979, 'e41c3169d55e34d21b7be44da4f433b5420909f909420d7a99db5d2984181a7c')
ON CONFLICT (id) DO NOTHING;
INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000002', '10000000-0000-0000-0000-000000000002', 'POST_ATTACHMENT', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/So_do_gian_hang_CICT_JobFair_2026.pdf', 'So_do_gian_hang_CICT_JobFair_2026.pdf', 'application/pdf', 108427, '5434c2057a4cf88c5f1119143a3c7672ca45d0a60216d7990c4f2319a75ff64a')
ON CONFLICT (id) DO NOTHING;
INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000003', '10000000-0000-0000-0000-000000000002', 'POST_ATTACHMENT', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/Danh_sach_40_Doanh_nghiep_tuyen_dung.xlsx', 'Danh_sach_40_Doanh_nghiep_tuyen_dung.xlsx', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', 8017, '3d26b4b9c5636cc74b0f9cfd2d7992084bd5342b314e817eb592a24e028d55a3')
ON CONFLICT (id) DO NOTHING;

UPDATE career_guide_posts
SET cover_image_url = '/api/v1/public/media/c9c90000-0000-4000-8000-000000000001',
    gallery_images = '[{"mediaId": "c9c90000-0000-4000-8000-000000000001", "url": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000001", "fileName": "post-seed-job-fair-2026.jpg"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000001' AND cover_image_url LIKE 'https://images.unsplash.com/%';
UPDATE career_guide_posts
SET attachment_urls = '[{"id": "c9c90000-0000-4000-8000-000000000002", "fileName": "So_do_gian_hang_CICT_JobFair_2026.pdf", "fileUrl": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000002", "fileSize": "106 KB", "fileType": "PDF"}, {"id": "c9c90000-0000-4000-8000-000000000003", "fileName": "Danh_sach_40_Doanh_nghiep_tuyen_dung.xlsx", "fileUrl": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000003", "fileSize": "8 KB", "fileType": "XLSX"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000001' AND attachment_urls::text LIKE '%example.com%';

INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000004', '10000000-0000-0000-0000-000000000002', 'POST_IMAGE', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/post-seed-japan-2027.jpg', 'post-seed-japan-2027.jpg', 'image/jpeg', 733874, '9719e0d1d01a8d93229f89fb2cbfbdbce2d3ad8a54371800e76163e402a34608')
ON CONFLICT (id) DO NOTHING;
INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000005', '10000000-0000-0000-0000-000000000002', 'POST_ATTACHMENT', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/Thong_bao_tuyen_chon_Internship_Japan_2027.pdf', 'Thong_bao_tuyen_chon_Internship_Japan_2027.pdf', 'application/pdf', 79721, '53bb4f6cb5d2d43e2c0096f4231fc2cf3ba249229a5efd35805eb85c814caa1f')
ON CONFLICT (id) DO NOTHING;

UPDATE career_guide_posts
SET cover_image_url = '/api/v1/public/media/c9c90000-0000-4000-8000-000000000004',
    gallery_images = '[{"mediaId": "c9c90000-0000-4000-8000-000000000004", "url": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000004", "fileName": "post-seed-japan-2027.jpg"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000002' AND cover_image_url LIKE 'https://images.unsplash.com/%';
UPDATE career_guide_posts
SET attachment_urls = '[{"id": "c9c90000-0000-4000-8000-000000000005", "fileName": "Thong_bao_tuyen_chon_Internship_Japan_2027.pdf", "fileUrl": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000005", "fileSize": "78 KB", "fileType": "PDF"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000002' AND attachment_urls::text LIKE '%example.com%';

INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000006', '10000000-0000-0000-0000-000000000002', 'POST_IMAGE', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/post-seed-quy-trinh-thuc-tap.jpg', 'post-seed-quy-trinh-thuc-tap.jpg', 'image/jpeg', 104803, '956b072603138ad6518e184e9e2b16a3da29e439e7201ef9b64e3d0b242038c3')
ON CONFLICT (id) DO NOTHING;
INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000007', '10000000-0000-0000-0000-000000000002', 'POST_ATTACHMENT', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/So_tay_Huong_dan_Thuc_tap_CICT_2026.pdf', 'So_tay_Huong_dan_Thuc_tap_CICT_2026.pdf', 'application/pdf', 148789, 'b41b07c5c2668ff1ecfd5dc1e8ab7c4a457c6b9f13f8a6cdf9b1458fb7fe3aed')
ON CONFLICT (id) DO NOTHING;
INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000008', '10000000-0000-0000-0000-000000000002', 'POST_ATTACHMENT', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/Bo_Bieu_Mau_Thuc_Tap_M01_M05.docx', 'Bo_Bieu_Mau_Thuc_Tap_M01_M05.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 37770, '9ab80b97a8889063ed7f502f76afd0f8b2c19a78c7c035c4f4ae1d1408dda820')
ON CONFLICT (id) DO NOTHING;

UPDATE career_guide_posts
SET cover_image_url = '/api/v1/public/media/c9c90000-0000-4000-8000-000000000006',
    gallery_images = '[{"mediaId": "c9c90000-0000-4000-8000-000000000006", "url": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000006", "fileName": "post-seed-quy-trinh-thuc-tap.jpg"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000003' AND cover_image_url LIKE 'https://images.unsplash.com/%';
UPDATE career_guide_posts
SET attachment_urls = '[{"id": "c9c90000-0000-4000-8000-000000000007", "fileName": "So_tay_Huong_dan_Thuc_tap_CICT_2026.pdf", "fileUrl": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000007", "fileSize": "145 KB", "fileType": "PDF"}, {"id": "c9c90000-0000-4000-8000-000000000008", "fileName": "Bo_Bieu_Mau_Thuc_Tap_M01_M05.docx", "fileUrl": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000008", "fileSize": "37 KB", "fileType": "DOCX"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000003' AND attachment_urls::text LIKE '%example.com%';

INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000009', '10000000-0000-0000-0000-000000000002', 'POST_IMAGE', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/post-seed-cv-phong-van.jpg', 'post-seed-cv-phong-van.jpg', 'image/jpeg', 111996, '225aaacaa5428cc9e745618e636b082c9a4d05ec72194f3d49f4a176e2d44f58')
ON CONFLICT (id) DO NOTHING;
INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000010', '10000000-0000-0000-0000-000000000002', 'POST_ATTACHMENT', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/Mau_CV_Chuan_IT_Intern_CICT.docx', 'Mau_CV_Chuan_IT_Intern_CICT.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 37542, '0dcee4d79f75e2f866ed5a17458ac3075e2c2df12f5c9342781565ac50bd33f9')
ON CONFLICT (id) DO NOTHING;

UPDATE career_guide_posts
SET cover_image_url = '/api/v1/public/media/c9c90000-0000-4000-8000-000000000009',
    gallery_images = '[{"mediaId": "c9c90000-0000-4000-8000-000000000009", "url": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000009", "fileName": "post-seed-cv-phong-van.jpg"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000004' AND cover_image_url LIKE 'https://images.unsplash.com/%';
UPDATE career_guide_posts
SET attachment_urls = '[{"id": "c9c90000-0000-4000-8000-000000000010", "fileName": "Mau_CV_Chuan_IT_Intern_CICT.docx", "fileUrl": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000010", "fileSize": "37 KB", "fileType": "DOCX"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000004' AND attachment_urls::text LIKE '%example.com%';

INSERT INTO media_assets (id, owner_user_id, purpose, context_id, scope, provider_file_id, file_name, mime_type, size_bytes, checksum)
VALUES ('c9c90000-0000-4000-8000-000000000011', '10000000-0000-0000-0000-000000000002', 'POST_IMAGE', '10000000-0000-0000-0000-000000000002', NULL, 'internlink/media/demo/career-guide/post-seed-workshop-ai-cloud.jpg', 'post-seed-workshop-ai-cloud.jpg', 'image/jpeg', 126302, 'fd2e9188d891bd717b687650a8dde51d89cc72974bae04d347a65825332dc5a4')
ON CONFLICT (id) DO NOTHING;

UPDATE career_guide_posts
SET cover_image_url = '/api/v1/public/media/c9c90000-0000-4000-8000-000000000011',
    gallery_images = '[{"mediaId": "c9c90000-0000-4000-8000-000000000011", "url": "/api/v1/public/media/c9c90000-0000-4000-8000-000000000011", "fileName": "post-seed-workshop-ai-cloud.jpg"}]'::jsonb
WHERE id = 'b0000000-0000-0000-0000-000000000005' AND cover_image_url LIKE 'https://images.unsplash.com/%';

