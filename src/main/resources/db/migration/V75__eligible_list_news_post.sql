-- Danh sách SV đủ điều kiện TTTT: Văn phòng Trường công bố theo đợt, mỗi kỳ một bài tin ghim
-- (mỗi đợt thêm một tệp Excel đính kèm). Xóa bài viết thì chỉ gỡ liên kết.
ALTER TABLE internship_terms
    ADD COLUMN eligible_list_post_id UUID REFERENCES career_guide_posts(id) ON DELETE SET NULL;
