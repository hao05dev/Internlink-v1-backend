-- Thư viện ảnh có thứ tự cho bài viết cẩm nang và tin tuyển dụng.
-- Mỗi phần tử: {"mediaId": uuid, "url": "/api/v1/public/media/<uuid>", "fileName": text, "caption": text?}
ALTER TABLE career_guide_posts ADD COLUMN gallery_images JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE job_positions ADD COLUMN gallery_images JSONB NOT NULL DEFAULT '[]'::jsonb;

-- Ảnh bìa nội bộ cũ trở thành phần tử đầu của gallery; URL ngoài (http/https) giữ nguyên ở cột cũ.
UPDATE career_guide_posts
SET gallery_images = jsonb_build_array(jsonb_build_object(
        'mediaId', substring(cover_image_url FROM '([0-9a-fA-F-]{36})$'),
        'url', cover_image_url,
        'fileName', 'anh-bia'))
WHERE cover_image_url ~ '^/api/v1/(public/)?media/[0-9a-fA-F-]{36}$';

UPDATE job_positions
SET gallery_images = jsonb_build_array(jsonb_build_object(
        'mediaId', substring(banner_url FROM '([0-9a-fA-F-]{36})$'),
        'url', banner_url,
        'fileName', 'banner'))
WHERE banner_url ~ '^/api/v1/(public/)?media/[0-9a-fA-F-]{36}$';
