# 🎓 InternLink — Cổng quản lý thực tập tốt nghiệp

[![CI](https://github.com/hao05dev/Internlink-v1-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/hao05dev/Internlink-v1-backend/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-14-black?logo=next.js&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-Python%203.10%2B-009688?logo=fastapi&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-336791?logo=postgresql&logoColor=white)

InternLink quản lý trọn quy trình thực tập tốt nghiệp giữa **sinh viên**, **doanh nghiệp** và **Trường CNTT&TT – Đại học Cần Thơ (CICT)**. Quy trình gồm:

- tìm nơi thực tập: tin tuyển dụng, AI gợi ý việc làm, phiếu tiếp nhận, giấy giới thiệu;
- trong kỳ: phân công GVHD, nhật ký tuần, họp, xử lý sự cố;
- cuối kỳ: hồ sơ nộp Khoa (Mẫu 04, M01–M05), chấm điểm theo phương án đánh giá/CLO, số hóa phiếu đánh giá giấy M-TT-03 bằng AI, công bố kết quả và thống kê.

Hệ thống gồm 3 repo:

| Repo | Vai trò | Công nghệ |
|---|---|---|
| **Internlink-v1-backend** (repo này) | API nghiệp vụ, phân quyền, lưu trữ, email, thông báo | Spring Boot 3.3, Java 21, JPA, Flyway, PostgreSQL 16 + pgvector |
| Internlink-v1-frontend (private) | Cổng web cho 7 vai trò | Next.js 14 App Router, TypeScript, Tailwind CSS, TanStack Query |
| Internlink-v1-ai (private) | Trích xuất kỹ năng, gợi ý việc làm, phân tích CV–JD, trợ lý CapyLink (RAG trên quy chế), đọc phiếu M-TT-03 | FastAPI, taxonomy kỹ năng IT (ESCO), Gemini / e5 (tùy chọn) |

---

## Hai cách sinh viên có nơi thực tập

Thực tế ở CICT, phần lớn doanh nghiệp không dùng hệ thống của Trường. Họ gửi tin qua email Trường và nhận hồ sơ qua email hoặc form của chính họ. Vì vậy mỗi tin tuyển có một **kênh nhận hồ sơ** (`job_positions.application_method`):

| Kênh | Ai đăng | Sinh viên làm gì | Kết quả được ghi nhận bằng |
|---|---|---|---|
| **EXTERNAL** (kênh của DN) | Văn phòng Trường đăng lại tin từ email DN; DN **không có tài khoản** | Bấm "Ứng tuyển qua kênh của DN" để mở link/email của DN; hệ thống đếm lượt quan tâm. Không nộp đơn trong app | SV tạo **Phiếu tiếp nhận** (chọn tin này làm tin nguồn) để được duyệt |
| **IN_APP** (ứng tuyển trong hệ thống) | DN đối tác có tài khoản tự đăng, Văn phòng Trường duyệt | Nộp đơn trong app; DN xét duyệt, phỏng vấn, gửi offer | Offer được chấp nhận, sau đó ký thỏa thuận 3 bên |
| Tự tìm (không qua tin) | Không có | Tự liên hệ DN, xin giấy giới thiệu nếu cần | **Phiếu tiếp nhận** |

Cả ba nhánh đều tạo ra một **lần thực tập** (`internship_placements`). Từ đó phần quản lý trong kỳ và cuối kỳ giống nhau. Mentor của DN không bắt buộc có tài khoản: GVHD có thể số hóa phiếu đánh giá giấy của cơ quan (M-TT-03).

---

## Kiến trúc

```mermaid
flowchart LR
    U[Người dùng<br/>7 vai trò] --> FE[Frontend<br/>Next.js 14 · :3000]
    FE -- REST + JWT cookie<br/>SSE thông báo --> BE[Backend<br/>Spring Boot · :8080]
    BE -- JPA / Flyway --> DB[(PostgreSQL 16<br/>+ pgvector)]
    BE -- REST + API key --> AI[AI service<br/>FastAPI · :8001]
    AI -. tùy chọn .-> LLM[Gemini API /<br/>e5 embedding cục bộ]
    BE --> ST[Lưu trữ tệp<br/>Local · Cloudflare R2 · Google Drive]
    BE --> EM[Email outbox<br/>Gmail SMTP / Gmail API]
    BE -. tùy chọn .-> RD[(Redis)]
    BE -. tùy chọn .-> CL[ClamAV<br/>quét tệp tải lên]
```

- **Backend** chia theo module nghiệp vụ trong `src/main/java/com/internlink/core/application`: `auth`, `student`, `company`, `recruitment`, `placement` (gồm hồ sơ nộp Khoa), `portfolio`, `evaluation` (gồm số hóa phiếu M-TT-03), `exception_case`, `organization` (kỳ, kế hoạch TTTT, danh sách SV), `meeting`, `announcement`, `assistant` (trợ lý CapyLink), `analytics`, `ai_matching`, `system`.
- **Cơ sở dữ liệu** được quản lý bằng Flyway (`src/main/resources/db/migration`), kèm dữ liệu demo. Embedding kỹ năng và tin tuyển dụng được lưu cache theo phiên bản model.
- **AI service** không truy cập DB. Backend gửi hồ sơ và tin tuyển, AI trả về kỹ năng chuẩn hóa, điểm phù hợp và lời giải thích. Khi AI không chạy, backend vẫn hoạt động; các chức năng AI báo lỗi rõ ràng thay vì làm hỏng luồng chính.
- **Đọc phiếu M-TT-03**: GVHD chụp phiếu đánh giá giấy của cơ quan, AI (Gemini) đọc 10 tiêu chí và phần nhận xét, GVHD soát, sửa rồi xác nhận. Khi xác nhận, điểm cơ quan được ghi và M03 trong hồ sơ nộp Khoa được duyệt luôn. Cả bản AI đọc và bản GVHD chốt đều được lưu để đo độ chính xác mà không cần gán nhãn tay.
- **Email** đi qua outbox, có các chế độ `DISABLED` / `REDIRECT` / `ALLOWLIST` / `LIVE` để không gửi nhầm cho người thật khi chạy thử.

---

## Ảnh màn hình

| Trang chủ | Danh sách việc làm |
|---|---|
| ![Trang chủ](assets/screenshots/01-home.png) | ![Việc làm](assets/screenshots/02-jobs.png) |
| **Sinh viên — tổng quan** | **Sinh viên — hỗ trợ AI** |
| ![Dashboard sinh viên](assets/screenshots/03-student-dashboard.png) | ![AI sinh viên](assets/screenshots/04-student-ai.png) |
| **Doanh nghiệp — quản lý tin tuyển** | **Khoa — tổng quan học kỳ** |
| ![Tin tuyển doanh nghiệp](assets/screenshots/05-company-jobs.png) | ![Dashboard khoa](assets/screenshots/07-faculty-dashboard.png) |
| **Khoa — thống kê** | **Giảng viên — chấm điểm & CLO** |
| ![Thống kê](assets/screenshots/08-faculty-analytics.png) | ![Chấm điểm](assets/screenshots/09-lecturer-grading.png) |
| **Mentor — theo dõi nhật ký** | |
| ![Mentor](assets/screenshots/10-mentor-weekly.png) | |

Ảnh chụp từ dữ liệu seed demo bằng `node scripts/capture-screenshots.mjs assets/screenshots`. Script cần cả 3 service đang chạy.

---

## Tính năng theo vai trò

| Vai trò | Chức năng chính |
|---|---|
| **Sinh viên** | Hồ sơ và CV, tìm và lưu việc làm, ứng tuyển trong app hoặc qua kênh của DN, phiếu tiếp nhận, giấy giới thiệu, thỏa thuận 3 bên, nhật ký ngày/tuần, họp, nộp hồ sơ nộp Khoa và báo cáo cuối kỳ, gợi ý việc làm và phân tích CV–JD bằng AI, hỏi trợ lý CapyLink |
| **Doanh nghiệp (HR)** | Hồ sơ công ty, đăng tin (Văn phòng Trường duyệt), quản lý ứng viên theo Kanban, phỏng vấn, offer, ký thỏa thuận 3 bên, phân công mentor |
| **Mentor** | Theo dõi và nhận xét nhật ký, đánh giá hằng tuần, đánh giá cuối kỳ (không bắt buộc có tài khoản) |
| **Giảng viên hướng dẫn** | Giám sát sinh viên, duyệt nhật ký, họp Google Meet, duyệt hồ sơ nộp Khoa, chấm theo phương án đánh giá/CLO, quét phiếu M-TT-03 bằng AI |
| **Ban quản lý thực tập (Khoa)** | Kỳ thực tập, danh sách sinh viên (import Excel/Google Sheets), phân công GVHD, duyệt phiếu tiếp nhận và thỏa thuận, phương án đánh giá, biểu mẫu, xử lý ngoại lệ, công bố kết quả, thống kê và phân tích khoảng trống kỹ năng |
| **Văn phòng Trường** | Kỳ và kế hoạch TTTT, duyệt doanh nghiệp và tin tuyển, đăng lại tin từ email DN (kênh ngoài), tiếp nhận phiếu, giấy giới thiệu, bài cẩm nang |
| **Quản trị hệ thống** | Người dùng, đơn vị, doanh nghiệp, kho tri thức CapyLink, quản lý AI, lưu trữ, nhật ký kiểm toán |

---

## Tài khoản demo

Tất cả tài khoản seed dùng mật khẩu chung: **`Password@123`** — đăng nhập tại `http://localhost:3000/login`.

| Vai trò | Email | Dashboard |
|---|---|---|
| ADMIN | `admin.cict@ctu.edu.vn` | `/admin/dashboard` |
| FACULTY_ADMIN | `qltt.cict@ctu.edu.vn` | `/faculty/dashboard` |
| SCHOOL_OFFICE | `vanphong.cict@ctu.edu.vn` | `/school-office/dashboard` |
| LECTURER | `gvhd.son@ctu.edu.vn` | `/lecturer/dashboard` |
| STUDENT | `b2110940@student.ctu.edu.vn` | `/student/dashboard` |
| COMPANY_REP | `tuyendung.fptct@fpt.com` (tab "Doanh nghiệp") | `/company/dashboard` |
| COMPANY_MENTOR | `mentor.nam@fpt.com` (tab "Doanh nghiệp") | `/mentor/dashboard` |

### Bộ dữ liệu demo đầy đủ (đợt "HK1 2026–2027 (Demo)")

Các script nằm ở `scripts/` của thư mục cha (cạnh 3 repo). Trên Windows chạy `reset-and-seed-demo.ps1` ở thư mục cha; script chạy 6 bước theo thứ tự dưới đây, dừng ngay khi có lỗi. Mỗi file đều chạy lại an toàn.

| Bước | File | Nội dung |
|---|---|---|
| 1 | `reset_demo.sql` | Xóa dữ liệu demo cũ, kể cả dữ liệu phát sinh khi thao tác demo |
| 2 | `seed_demo_full.sql` | Kỳ demo, tài khoản, DN đối tác, tin, đơn, lần thực tập, nhật ký, đánh giá (sinh từ `demo_data/build_demo.py`) |
| 3 | `seed_demo_dossier.sql` | Hồ sơ nộp Khoa (Mẫu 04, M01–M05) của các lần thực tập demo |
| 4 | `seed_term_plan_hk1_2026_2027.sql` | Kế hoạch TTTT của kỳ demo |
| 5 | `seed_real_mail_jobs.sql` | 39 tin thật nhận qua email Trường, kênh ngoài (đã đóng) |
| 6 | `seed_market_it_companies.sql` | 17 DN IT thật, 23 tin (9 đang mở) do Văn phòng Trường đăng, kênh ngoài |

Cùng mật khẩu chung ở trên. Gồm 60 SV, 8 GV, 12 DN đối tác (có tài khoản, tin ứng tuyển trong app), 48 tin tuyển, 78 đơn ứng tuyển, 34 đợt thực tập (đang thực tập / hoàn thành / chấm dứt), nhật ký tuần–ngày, phiếu đánh giá, hồ sơ nộp Khoa, 14 bài cẩm nang có ảnh bìa, thông báo, cuộc họp. Ngoài ra có 17 DN không có tài khoản, tin của họ do Văn phòng Trường đăng và SV ứng tuyển qua kênh của DN.

| Vai trò | Email | Tình huống |
|---|---|---|
| STUDENT | `b2398034@demo.internlink.vn` | Đang thực tập tại MediMekong, đã có 8 nhật ký tuần |
| STUDENT | `b2398055@demo.internlink.vn` | Đã hoàn thành, có kết quả cuối |
| STUDENT | `b2398007@demo.internlink.vn` | Đang ứng tuyển, chưa có nơi thực tập |
| LECTURER | `gv.hoangphuc@demo.internlink.vn` | GVHD của `b2398034` |
| LECTURER | `gv.baotran@demo.internlink.vn` | GVHD của `b2398055` |
| COMPANY_REP | `tuyendung.medimekong@demo.internlink.vn` | Doanh nghiệp MediMekong |
| COMPANY_MENTOR | `mentor.mai19.medimekong@demo.internlink.vn` | Mentor của `b2398034` |
| FACULTY_ADMIN | `khoa@demo.internlink.vn` | Ban quản lý Khoa |
| SCHOOL_OFFICE | `vanphongtruong@demo.internlink.vn` | Văn phòng Trường |

Danh sách đầy đủ (đuôi `@demo.internlink.vn`, cùng mật khẩu chung):

- **Giảng viên (8)**: `gv.thanhtam`, `gv.hoangphuc`, `gv.minhthu`, `gv.quockhanh`, `gv.ngocdiem`, `gv.ducthinh`, `gv.baotran`, `gv.huuloc` — mỗi GV có 7–8 SV trong danh sách, 4–5 SV đã có nơi thực tập.
- **Doanh nghiệp (12)**: `tuyendung.<slug>` với slug là `songhau`, `mekongdata`, `ninhkieudigital`, `phusapay`, `cuulongsecure`, `taydocloud`, `chonoi`, `luavangedu`, `dongbangxanh`, `hoasengames`, `medimekong`, `cairanglogistics`.
- **Mentor**: `mentor.<tên><số>.<slug>`, ví dụ `mentor.phat01.songhau`, `mentor.mai19.medimekong`, `mentor.tri20.cairanglogistics` (mỗi DN 1–2 mentor).
- **Sinh viên (60)**: `b23980NN` (MSSV B2398001–B2398060), theo trạng thái:

| MSSV | Trạng thái |
|---|---|
| 001–006 | Chưa ứng tuyển |
| 007–014 | Đang ứng tuyển |
| 015–020 | Đang phỏng vấn |
| 021–024 | Có offer chờ phản hồi |
| 025 | Thỏa thuận 3 bên chờ ký |
| 026–027 | Phiếu tiếp nhận (tự tìm) chờ duyệt |
| 028–029 | Chuẩn bị thực tập (bắt đầu 23/11/2026) |
| 030–033 | Tự tìm nơi thực tập, đã được duyệt |
| 034–054 | Đang thực tập tại DN đối tác (040: sự cố vắng mặt, 045: chuyển đơn vị, 052: chấm dứt sớm) |
| 055–060 | Đã kết thúc (059: chờ GV chấm, 060: không đạt) |

Ngày "hôm nay" của kịch bản là 18/11/2026. Ảnh và PDF demo nằm trên R2 dưới `media/demo/` và `private/documents/demo/`; sinh lại và tải lên bằng `python scripts/demo_data/build_demo.py --upload` (đọc khóa R2 từ `.env`). `reset_demo.sql` không xoá file trên R2.

Lưu ý: seed chuyển kỳ gốc `HK2-2026-2027` (bắt đầu 15/02/2027) từ ACTIVE sang REGISTRATION_OPEN, để kỳ demo là kỳ ACTIVE duy nhất và được dashboard Khoa/VP Trường chọn mặc định. `reset_demo.sql` trả kỳ gốc về ACTIVE.

Các tài khoản này chỉ dành cho môi trường phát triển. Khi triển khai thật, hãy đổi mật khẩu hoặc bỏ migration seed.

---

## Chạy trên máy

Yêu cầu: JDK 21, Maven 3.9+, Node.js 20+, Python 3.10+, Docker (cho PostgreSQL). Đặt 3 repo cạnh nhau trong cùng một thư mục cha.

**1. PostgreSQL (+ pgvector) và Redis**

```bash
cp .env.example .env        # điền DB_PASSWORD, JWT_SECRET, ...
docker compose up -d postgres redis
```

**2. Backend — `:8080`**

```bash
mvn spring-boot:run
```

Flyway tự tạo schema và nạp tài khoản seed gốc; bộ dữ liệu demo đầy đủ nạp riêng như mục trên. Swagger UI: `http://localhost:8080/swagger-ui.html`.

**3. AI service — `:8001`**

```bash
cd ../Internlink-v1-ai
cp .env.example .env        # mặc định dùng provider cục bộ, không cần API key
pip install -r requirements.txt
uvicorn app.main:app --port 8001
```

Đọc phiếu M-TT-03 và trợ lý CapyLink dạng hội thoại cần `GEMINI_API_KEY`; không có khóa thì CapyLink chạy chế độ `extractive` (trích nguyên văn quy chế). Hoặc chạy bằng Docker từ repo backend: `docker compose up -d ai-service`. Tài liệu API: `http://localhost:8001/docs`.

**4. Frontend — `:3000`**

```bash
cd ../Internlink-v1-frontend
cp .env.example .env.local  # BACKEND_API_ORIGIN=http://localhost:8080
npm install
npm run dev
```

---

## Kiểm thử và CI

Mỗi repo có GitHub Actions chạy trên mọi push và pull request vào `main`.

| Repo | Lệnh | Nội dung |
|---|---|---|
| Backend | `mvn verify` | ~580 test JUnit/Mockito; test tích hợp chạy trên H2 và PostgreSQL 16 + pgvector (service container trong CI) |
| Frontend | `npm run lint`, `npx tsc --noEmit`, `npm test` | ESLint, kiểm tra kiểu TypeScript, 674 test Vitest trong 135 file |
| AI | `python -m pytest` | ~160 test pytest trên Python 3.10 và 3.13, gồm test cho benchmark |

---

## Đánh giá thuật toán gợi ý việc làm

AI service xếp hạng tin tuyển cho sinh viên bằng **Skill + Vector Hybrid**: điểm kỹ năng (0.8 × kỹ năng bắt buộc + 0.2 × kỹ năng tùy chọn, sau chuẩn hóa qua taxonomy và từ đồng nghĩa) kết hợp với độ tương đồng vector.

Benchmark tổng hợp gồm 150 sinh viên × 30 tin mỗi người. Hồ sơ bị nhiễu: khai thiếu ~35% kỹ năng, mô tả dùng từ đồng nghĩa hoặc câu diễn giải, và 20% tin là tin mồi. Nhãn suy ra từ tập kỹ năng ẩn, không gán tay.

| Phương pháp | nDCG@10 | KTC 95% |
|---|:-:|:-:|
| BM25 | 0.335 | [0.305, 0.369] |
| Jaccard | 0.350 | [0.319, 0.384] |
| Vector cosine (hashing) | 0.435 | [0.401, 0.468] |
| Skill-Only | **0.863** | [0.835, 0.889] |
| Weighted Features | 0.861 | [0.833, 0.887] |
| Skill + Vector Hybrid | 0.858 | [0.830, 0.884] |

Kết luận:
- Chuẩn hóa và khớp theo kỹ năng là yếu tố quyết định: nDCG@10 cao hơn ~0.42–0.52 so với các phương pháp chỉ dựa trên văn bản (p < 0.001), và ổn định qua 5 seed.
- Với vector hashing hiện tại, Hybrid **chưa tốt hơn** Skill-Only (chênh −0.006, p = 0.18, không có ý nghĩa thống kê). Bước tiếp theo là chạy lại benchmark với embedding ngữ nghĩa thật (e5 / Gemini).

Chi tiết cách sinh dữ liệu, kiểm định thống kê và giới hạn nằm trong repo AI: `evaluation-reports/hard_benchmark_summary.md`.

---

## Bản quyền

Đề tài luận văn tốt nghiệp — Trường Công nghệ Thông tin & Truyền thông, Đại học Cần Thơ (CICT – CTU).
