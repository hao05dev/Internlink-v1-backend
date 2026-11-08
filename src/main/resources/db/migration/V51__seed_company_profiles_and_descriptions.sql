-- ====================================================================
-- INTERNLINK MIGRATION V51: SEED OFFICIAL DESCRIPTIONS AND PROFILES FOR 5 PARTNER COMPANIES
-- Updates full company descriptions, employee ranges, office addresses, and map links
-- ====================================================================

-- 1. FPT Software Cần Thơ
UPDATE companies
SET 
    description = '<p><strong>FPT Software Cần Thơ</strong> là chi nhánh chiến lược của Công ty TNHH Phần mềm FPT (thành viên Tập đoàn FPT) tại khu vực Đồng bằng sông Cửu Long. Với sứ mệnh đưa trí tuệ công nghệ miền Tây vươn tầm thế giới, FPT Software Cần Thơ chuyên cung cấp các giải pháp và dịch vụ chuyển đổi số toàn diện bao gồm: Phát triển phần mềm theo yêu cầu, Điện toán đám mây (Cloud), Trí tuệ nhân tạo (AI/Data Science), Tự động hóa quy trình nghiệp vụ và Hệ thống phần mềm ô tô (Automotive).</p><p>Trải qua quá trình phát triển mạnh mẽ, chi nhánh đã xây dựng môi trường làm việc hiện đại chuẩn Campus công nghệ cao, thu hút hàng trăm kỹ sư tài năng từ các trường đại học hàng đầu khu vực, đặc biệt là Đại học Cần Thơ. Công ty luôn chú trọng chính sách đãi ngộ, văn hóa làm việc toàn cầu (Global Mindset) và lộ trình phát triển nghề nghiệp rõ ràng cho từng nhân viên.</p><p>FPT Software Cần Thơ là đối tác chiến lược lâu năm của Trường Công nghệ Thông tin & Truyền thông (CICT - CTU), thường xuyên tiếp nhận sinh viên thực tập các ngành Công nghệ thông tin, Kỹ thuật phần mềm, Hệ thống thông tin và Khoa học máy tính với chương trình đào tạo On-the-job Training thực chiến cùng các chuyên gia giàu kinh nghiệm.</p>',
    employee_range = '1.000 - 5.000 nhân viên',
    office_address = 'Đường số 1, Khu đô thị Nam Long, Phường Hưng Thạnh, Quận Cái Răng, TP. Cần Thơ',
    maps_url = 'https://maps.google.com/?q=FPT+Software+Can+Tho'
WHERE id = '33333333-3333-3333-3333-333333333333';

-- 2. VNPT Cần Thơ - Trung tâm Công nghệ Thông tin
UPDATE companies
SET 
    description = '<p><strong>Trung tâm Công nghệ Thông tin - VNPT Cần Thơ</strong> (VNPT IT Cần Thơ) trực thuộc Tập đoàn Bưu chính Viễn thông Việt Nam, là đơn vị công nghệ chủ lực dẫn dắt công cuộc chuyển đổi số tại TP. Cần Thơ và vùng Đồng bằng sông Cửu Long. VNPT Cần Thơ là đối tác tin cậy của các cơ quan chính quyền địa phương, tổ chức giáo dục, y tế và cộng đồng doanh nghiệp trong việc triển khai hạ tầng số và giải pháp phần mềm thông minh.</p><p>Các lĩnh vực hoạt động mũi nhọn bao gồm: Xây dựng giải pháp Chính quyền điện tử và Đô thị thông minh (Smart City), Nền tảng y tế số (VNPT-HIS), Hệ sinh thái giáo dục số (vnEdu), Hệ thống quản trị doanh nghiệp (VNPT-ERP) và các giải pháp an toàn thông tin mạng.</p><p>Hợp tác chặt chẽ với Trường ĐH Cần Thơ, VNPT Cần Thơ mang đến cho sinh viên thực tập cơ hội tiếp cận trực tiếp với các dự án chuyển đổi số cấp tỉnh/thành phố, rèn luyện kỹ năng phát triển hệ thống quy mô lớn, kiến trúc hướng dịch vụ (Microservices), cơ sở dữ liệu phân tán và công nghệ mạng viễn thông thế hệ mới.</p>',
    employee_range = '500 - 1.000 nhân viên',
    office_address = 'Số 02 Nguyễn Trãi, Phường Tân An, Quận Ninh Kiều, TP. Cần Thơ',
    maps_url = 'https://maps.google.com/?q=VNPT+Can+Tho'
WHERE id = '44444444-4444-4444-4444-444444444444';

-- 3. LG CNS Việt Nam
UPDATE companies
SET 
    description = '<p><strong>LG CNS Việt Nam</strong> là công ty con của LG CNS - tập đoàn công nghệ thông tin và dịch vụ số hàng đầu trực thuộc Tập đoàn LG (Hàn Quốc). Với hơn 30 năm kinh nghiệm dẫn dắt thị trường giải pháp doanh nghiệp tại châu Á và toàn cầu, LG CNS chuyên nghiên cứu và cung cấp các giải pháp công nghệ cao cấp cho các tập đoàn đa quốc gia và hệ sinh thái sản xuất thông minh.</p><p>Các thế mạnh cốt lõi của LG CNS bao gồm: Chuyển đổi số nhà máy thông minh (Smart Factory), Kiến trúc đám mây lai (Hybrid Cloud), Phân tích dữ liệu lớn và AI (Big Data & Machine Learning), Giải pháp quản trị chuỗi cung ứng (SCM/Logistics) và Hệ thống tài chính - ngân hàng số.</p><p>Chương trình thực tập sinh tài năng tại LG CNS là bệ phóng vững chắc cho sinh viên ngành CNTT muốn trải nghiệm phong cách làm việc chuyên nghiệp chuẩn quốc tế, tham gia các dự án phát triển phần mềm toàn cầu, nâng cao năng lực ngoại ngữ và tích lũy kiến thức chuyên sâu trong môi trường R&D hiện đại bậc nhất.</p>',
    employee_range = '1.000 - 3.000 nhân viên',
    office_address = 'Tầng 15, Tòa nhà Bitexco Financial Tower, Số 2 Hải Triều, Phường Bến Nghé, Quận 1, TP. Hồ Chí Minh',
    maps_url = 'https://maps.google.com/?q=Bitexco+Financial+Tower'
WHERE id = 'a1b2c3d4-0000-0000-0000-111122223333';

-- 4. Viettel Cần Thơ
UPDATE companies
SET 
    description = '<p><strong>Viettel Cần Thơ</strong> là chi nhánh của Tập đoàn Công nghiệp - Viễn thông Quân đội (Viettel) – doanh nghiệp viễn thông và công nghệ số số 1 Việt Nam và thuộc Top 30 thương hiệu viễn thông giá trị nhất thế giới. Tại Cần Thơ, Viettel luôn khẳng định vị thế tiên phong về hạ tầng mạng băng rộng, mạng 4G/5G và hệ sinh thái giải pháp số phục vụ chuyển đổi số toàn diện cho địa phương.</p><p>Viettel Cần Thơ tập trung phát triển các giải pháp số hóa dịch vụ công, giải pháp thanh toán điện tử (Viettel Money), trung tâm điều hành thông minh (IOC), nền tảng Internet vạn vật (IoT) và hạ tầng điện toán đám mây Viettel Cloud an toàn, bảo mật cao.</p><p>Môi trường làm việc tại Viettel được định hình bởi văn hóa kỷ luật quân đội, tinh thần đổi mới sáng tạo không ngừng và khát vọng phụng sự cộng đồng. Sinh viên thực tập tại Viettel Cần Thơ được giao các bài toán thực tế, cọ xát với hệ thống chịu tải lớn, đồng thời được cố vấn bởi đội ngũ kỹ sư viễn thông - CNTT dày dặn kinh nghiệm.</p>',
    employee_range = '500 - 1.000 nhân viên',
    office_address = 'Số 210 Trần Phú, Phường Cái Khế, Quận Ninh Kiều, TP. Cần Thơ',
    maps_url = 'https://maps.google.com/?q=210+Tran+Phu+Can+Tho'
WHERE id = 'b2c3d4e5-0000-0000-0000-222233334444';

-- 5. TMA Solutions
UPDATE companies
SET 
    description = '<p><strong>TMA Solutions</strong> được thành lập từ năm 1997, là một trong những tập đoàn công nghệ phần mềm và R&D hàng đầu Việt Nam với hơn 4.000 kỹ sư chất lượng cao. TMA phục vụ khách hàng là các tập đoàn công nghệ hàng đầu đến từ 30 quốc gia trên thế giới, bao gồm Bắc Mỹ, châu Âu, Nhật Bản, Úc và châu Á.</p><p>Tại khu vực Tây Nam Bộ, TMA đầu tư xây dựng các trung tâm phát triển phần mềm và công viên sáng tạo nhằm quy tụ nhân lực công nghệ trẻ. Các lĩnh vực chuyên môn sâu của TMA gồm có: Viễn thông (Telecom & 5G), Tài chính - Bảo hiểm số (Fintech & Insurtech), Phần mềm ô tô (Automotive Software), Y tế số (Healthtech), Trí tuệ nhân tạo (AI/Data Science) và IoT.</p><p>TMA Solutions là đối tác hợp tác chiến lược mật thiết của Trường ĐH Cần Thơ. Hàng năm, công ty tiếp nhận hàng trăm sinh viên CICT - CTU tham gia chương trình thực tập doanh nghiệp, tài trợ phòng Lab nghiên cứu và tuyển dụng trực tiếp các sinh viên xuất sắc vào đội ngũ kỹ sư chính thức ngay sau khi tốt nghiệp.</p>',
    employee_range = '1.000 - 5.000 nhân viên',
    office_address = 'Khu Công viên Phần mềm TMA, Đường 3/2, Phường Xuân Khánh, Quận Ninh Kiều, TP. Cần Thơ',
    maps_url = 'https://maps.google.com/?q=TMA+Solutions+Can+Tho'
WHERE id = 'c3d4e5f6-0000-0000-0000-333344445555';
