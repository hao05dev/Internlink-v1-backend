-- Kế hoạch TTTT: tệp văn bản gốc (PDF scan) đính kèm kỳ và bài tin tức công khai tạo khi công bố.
-- Xóa tệp hoặc bài viết thì chỉ gỡ liên kết, không ảnh hưởng các mốc đã lưu.
ALTER TABLE internship_terms
    ADD COLUMN plan_document_media_id UUID REFERENCES media_assets(id) ON DELETE SET NULL,
    ADD COLUMN plan_news_post_id UUID REFERENCES career_guide_posts(id) ON DELETE SET NULL;
