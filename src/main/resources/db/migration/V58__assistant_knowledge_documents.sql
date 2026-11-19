-- Văn bản chính thức (quy định, đề cương) làm nguồn tri thức cho trợ lý CapyLink.
-- Chỉ chứa nội dung công khai; đã bỏ thông tin liên hệ cá nhân của giảng viên.
CREATE TABLE IF NOT EXISTS assistant_knowledge_documents (
    id          UUID PRIMARY KEY,
    title       VARCHAR(300) NOT NULL,
    category    VARCHAR(50)  NOT NULL DEFAULT 'REGULATIONS_GUIDELINES',
    source_url  VARCHAR(500),
    content     TEXT         NOT NULL,
    visibility  VARCHAR(20)  NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    issued_on   DATE,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO assistant_knowledge_documents (id, title, source_url, issued_on, content)
VALUES ('b0389ccc-6c43-5775-8616-8a0d7cab9f7b', 'Đề cương học phần Thực tập thực tế - Tin học (CT429)', 'https://cit.ctu.edu.vn/decuong/CT429.pdf', NULL, $doc$
Học phần: Thực tập thực tế - Tin học (Internships in informatics)
Mã số: CT429. Số tín chỉ: 2. Thời lượng: 8 tuần thực tập tại cơ quan, doanh nghiệp (180 tiết thực tế). Đơn vị phụ trách: Khoa CNTT-TT, Trường Đại học Cần Thơ.

## Điều kiện tiên quyết
Sinh viên phải tích lũy được ít nhất 100 tín chỉ (≥ 100 TC) mới được đăng ký học phần Thực tập thực tế CT429.

## Mục tiêu đợt thực tập
Trong chương trình đào tạo các chuyên ngành công nghệ thông tin, sinh viên phải trải qua 8 tuần thực tập thực tế. Mục đích của đợt thực tập là:
1. Củng cố kiến thức lý thuyết đã được trang bị trong nhà trường và vận dụng những kiến thức đó vào thực tế.
2. Rèn luyện kỹ năng thực hành thông qua việc thực hiện các công việc thực tiễn mà cơ quan tiếp nhận sinh viên thực tập giao cho.
3. Rèn luyện ý thức chấp hành kỷ luật lao động, thái độ giao tiếp với mọi người, phát huy tinh thần học hỏi, chủ động sáng tạo trong việc giải quyết các vấn đề của thực tiễn sản xuất, lao động và cuộc sống.

## Phương pháp giảng dạy và đánh giá
Giảng viên hỗ trợ chuyên môn để sinh viên giải quyết vấn đề trong thời gian thực tập. Đánh giá học phần: các thành phần khác chiếm 100%.

## Nội dung công việc thực tập
Khối kiến thức thực tập phải đạt ít nhất 1 trong 3 lĩnh vực sau.
1. Hệ thống thông tin (chọn ít nhất 1 công việc): xây dựng hệ thống thông tin quản lý phục vụ nhu cầu cụ thể của cơ quan; xây dựng trang web giới thiệu cơ quan và lĩnh vực hoạt động; xây dựng hệ thống thông tin phục vụ thương mại điện tử, chính phủ điện tử; thực hiện nội dung số như bài giảng điện tử, báo điện tử.
2. Mạng máy tính và truyền thông (chọn ít nhất 1 công việc): tham gia thiết kế, lắp đặt và cấu hình mạng nội bộ (server và client); cấu hình dịch vụ Internet server (web server, mail server); nghiên cứu, cài đặt giải pháp an toàn, an ninh mạng; lập trình truyền thông.
3. Kỹ thuật phần mềm (chọn ít nhất 1 công việc): tham gia phân tích và thiết kế phần mềm; cài đặt phần mềm (lập trình và kiểm thử module); kiểm thử phần mềm (tích hợp và kiểm thử hệ thống); triển khai phần mềm; bảo trì phần mềm; quản lý dự án phần mềm.
Ngoài ra sinh viên có thể tham gia các hoạt động khác như tin học hoá văn phòng (Word, Excel, PowerPoint), lắp ráp, cài đặt, cấu hình và sửa chữa máy tính, tìm hiểu các công nghệ mới.

## Báo cáo thực tập
Sau khi kết thúc đợt thực tập, mỗi nhóm sinh viên thực tập phải nộp một báo cáo thực tập theo mẫu báo cáo thực tập (M-TT-01).

## Tài liệu học phần
Nơi thực tập sẽ cung cấp tài liệu, hoặc sinh viên tự tìm kiếm theo yêu cầu của công việc.
$doc$)
ON CONFLICT (id) DO NOTHING;
