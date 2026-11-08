package com.internlink.core.application.organization;

import com.internlink.core.application.portfolio.PortfolioDocxExporter;
import com.internlink.core.infrastructure.integration.storage.OfficialTemplateStore;
import com.internlink.core.shared.enums.ReportTemplateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Component
public class SampleTemplateDocumentGenerator {

    private final OfficialTemplateStore officialTemplateStore;
    private final PortfolioDocxExporter portfolioDocxExporter;

    public SampleTemplateDocumentGenerator(OfficialTemplateStore store) {
        this.officialTemplateStore = store;
        this.portfolioDocxExporter = null;
    }

    @Autowired
    public SampleTemplateDocumentGenerator(OfficialTemplateStore store, @Autowired(required = false) PortfolioDocxExporter exporter) {
        this.officialTemplateStore = store;
        this.portfolioDocxExporter = exporter;
    }

    public Resource generateSample(String title, ReportTemplateType type, String programCode) {
        String officialFile = resolveOfficialFileName(type, programCode);
        if (officialFile != null) {
            Resource officialRes = loadOfficialResource(officialFile);
            if (officialRes != null) {
                return officialRes;
            }
        }

        String docTitle = title != null && !title.isBlank() ? title : resolveDefaultTitle(type, programCode);
        String htmlContent = buildWordHtmlFallback(docTitle, type);
        return new ByteArrayResource(htmlContent.getBytes(StandardCharsets.UTF_8));
    }

    public Resource generateSampleDocx(String title, ReportTemplateType type, String programCode) {
        String officialDocx = resolveOfficialDocxFileName(type, programCode);
        if (officialDocx != null) {
            Resource res = loadOfficialResource(officialDocx);
            if (res != null) {
                return res;
            }
        }

        if (portfolioDocxExporter != null) {
            try {
                String kind = mapTypeToKind(type);
                if (kind != null) {
                    Map<String, Object> meta = buildDefaultDocxMeta(type, programCode);
                    Map<String, Object> content = buildDefaultDocxContent(type);
                    byte[] docx = portfolioDocxExporter.export(kind, content, meta, false);
                    return new ByteArrayResource(docx);
                }
            } catch (Exception ex) {
                log.warn("Không thể sinh docx bằng PortfolioDocxExporter: {}", ex.getMessage());
            }
        }

        return generateSample(title, type, programCode);
    }

    /** Tải biểu mẫu chính thức từ R2 hoặc local; trả về null nếu chưa có tệp. */
    public Resource loadOfficialResource(String fileName) {
        return officialTemplateStore.find(fileName).orElse(null);
    }

    public String resolveOfficialFileName(ReportTemplateType type, String programCode) {
        String prog = (programCode != null) ? programCode.trim().toUpperCase() : "DEFAULT";
        return switch (type) {
            case FORM_04_RECEPTION_8W -> "04-Phieu_nhan_SV.doc";
            case FORM_04_RECEPTION_12W -> "04-Phieu_nhan_SV_12tuan.doc";
            case M01 -> "M-TT-01-Phieugiaoviec.doc";
            case M02 -> "M-TT-02-Phieutheodoi.doc";
            case M03 -> "M-TT-03-pdgia_coquan.doc";
            case M04, M04_LECTURER_COMMON -> {
                if ("KHMT".equals(prog) || "CS".equals(prog)) yield "M-TT-04-Phieu_danh_gia_danh cho GVHD-2025-KHMT.doc";
                if ("MMT".equals(prog) || "NE".equals(prog)) yield "M-TT-04-Phieu_danh_gia_danh cho GVHD-2025_MMT.doc";
                yield "M-TT-04-Phieu_danh_gia_danh cho GVHD-2025.doc";
            }
            case M04_LECTURER_KHMT -> "M-TT-04-Phieu_danh_gia_danh cho GVHD-2025-KHMT.doc";
            case M04_LECTURER_MMT -> "M-TT-04-Phieu_danh_gia_danh cho GVHD-2025_MMT.doc";
            case M05, M05_REPORT_GUIDELINE, FINAL_REPORT -> "M-TT-05-Maubaocao.doc";
            default -> null;
        };
    }

    public String resolveOfficialDocxFileName(ReportTemplateType type, String programCode) {
        String prog = (programCode != null) ? programCode.trim().toUpperCase() : "DEFAULT";
        return switch (type) {
            case FORM_04_RECEPTION_8W -> "04-Phieu_nhan_SV.docx";
            case FORM_04_RECEPTION_12W -> "04-Phieu_nhan_SV_12tuan.docx";
            case M01 -> "M-TT-01-Phieugiaoviec.docx";
            case M02 -> "M-TT-02-Phieutheodoi.docx";
            case M03 -> "M-TT-03-pdgia_coquan.docx";
            case M04, M04_LECTURER_COMMON -> "M-TT-04-Phieu_danh_gia_GVHD.docx";
            case M04_LECTURER_KHMT -> "M-TT-04-Phieu_danh_gia_GVHD-KHMT.docx";
            case M04_LECTURER_MMT -> "M-TT-04-Phieu_danh_gia_GVHD-MMT.docx";
            case M05, M05_REPORT_GUIDELINE, FINAL_REPORT -> "M-TT-05-Maubaocao.docx";
            default -> null;
        };
    }

    public String resolveDefaultFileName(ReportTemplateType type, String programCode) {
        String official = resolveOfficialFileName(type, programCode);
        if (official != null) return official;

        String prog = (programCode != null && !programCode.isBlank()) ? programCode.toUpperCase() : "CHUNG";
        return switch (type) {
            case M01 -> "M-TT-01-Phieugiaoviec.doc";
            case M02 -> "M-TT-02-Phieutheodoi.doc";
            case M03 -> "M-TT-03-pdgia_coquan.doc";
            case M04, M04_LECTURER_KHMT, M04_LECTURER_MMT, M04_LECTURER_COMMON -> "M-TT-04-Phieu_danh_gia_GVHD-" + prog + ".doc";
            case M05, M05_REPORT_GUIDELINE, FINAL_REPORT -> "M-TT-05-Maubaocao.doc";
            case FORM_04_RECEPTION_8W -> "04-Phieu_nhan_SV.doc";
            case FORM_04_RECEPTION_12W -> "04-Phieu_nhan_SV_12tuan.doc";
            default -> "Bieu_mau_thuc_tap_" + type.name() + ".doc";
        };
    }

    public String resolveDefaultDocxFileName(ReportTemplateType type, String programCode) {
        String official = resolveOfficialDocxFileName(type, programCode);
        if (official != null) return official;
        return "Bieu_mau_" + (type != null ? type.name() : "bieu-mau") + ".docx";
    }

    public String resolveDefaultTitle(ReportTemplateType type, String programCode) {
        String progDesc = (programCode != null && !programCode.equals("DEFAULT")) ? " - Ngành " + programCode : "";
        return switch (type) {
            case M01 -> "M-TT-01: Phiếu Giao Việc Thực Tập Doanh Nghiệp";
            case M02 -> "M-TT-02: Phiếu Theo Dõi Tiến Độ Thực Tập";
            case M03 -> "M-TT-03: Phiếu Đánh Giá Của Doanh Nghiệp Tiếp Nhận";
            case M04, M04_LECTURER_KHMT, M04_LECTURER_MMT, M04_LECTURER_COMMON -> "M-TT-04: Phiếu Đánh Giá Dành Cho Giảng Viên Hướng Dẫn" + progDesc;
            case M05, M05_REPORT_GUIDELINE, FINAL_REPORT -> "M-TT-05: Mẫu Đề Cương & Báo Cáo Tổng Kết Thực Tập Tốt Nghiệp";
            case FORM_04_RECEPTION_8W -> "Biểu mẫu 04: Phiếu Tiếp Nhận Sinh Viên Thực Tập (8 Tuần)";
            case FORM_04_RECEPTION_12W -> "Biểu mẫu 04: Phiếu Tiếp Nhận Sinh Viên Thực Tập (12 Tuần)";
            default -> "Biểu Mẫu Quy Định Thực Tập - " + type.name();
        };
    }

    private String mapTypeToKind(ReportTemplateType type) {
        if (type == null) return null;
        return switch (type) {
            case FORM_04_RECEPTION_8W, FORM_04_RECEPTION_12W -> "FORM04";
            case M01 -> "M01";
            case M02 -> "M02";
            case M03 -> "M03";
            case M04, M04_LECTURER_KHMT, M04_LECTURER_MMT, M04_LECTURER_COMMON -> "M04";
            case M05, M05_REPORT_GUIDELINE, FINAL_REPORT -> "M05";
            default -> null;
        };
    }

    private Map<String, Object> buildDefaultDocxMeta(ReportTemplateType type, String programCode) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("studentName", "...........................................................");
        meta.put("studentCode", "........................");
        meta.put("cohort", " khóa .....");
        meta.put("programName", programCode != null && !programCode.equals("DEFAULT") ? programCode : "Công nghệ Thông tin");
        meta.put("termName", "HỌC KỲ THỰC TẬP TỐT NGHIỆP");
        meta.put("companyName", "................................................................................");
        meta.put("companyAddress", "................................................................................");
        meta.put("companyPhone", "........................");
        meta.put("mentorName", "...........................................................");
        meta.put("mentorPhone", "........................");
        meta.put("mentorEmail", "........................@gmail.com");
        meta.put("lecturerName", "...........................................................");
        meta.put("departmentName", "TRƯỜNG CÔNG NGHỆ THÔNG TIN VÀ TRUYỀN THÔNG");
        meta.put("startDate", LocalDate.now().toString());
        meta.put("endDate", LocalDate.now().plusWeeks(type == ReportTemplateType.FORM_04_RECEPTION_12W ? 12 : 8).toString());
        meta.put("weekCount", type == ReportTemplateType.FORM_04_RECEPTION_12W ? 12 : 8);
        meta.put("components", List.of(
            Map.of("code", "REPORT", "name", "Báo cáo thực tập & kết quả công việc", "weight", 0.6, "assessorRole", "LECTURER"),
            Map.of("code", "ATTITUDE", "name", "Thái độ & tính chuyên cần", "weight", 0.4, "assessorRole", "LECTURER")
        ));
        return meta;
    }

    private Map<String, Object> buildDefaultDocxContent(ReportTemplateType type) {
        Map<String, Object> content = new LinkedHashMap<>();
        int weeksCount = type == ReportTemplateType.FORM_04_RECEPTION_12W ? 12 : 8;
        List<Map<String, Object>> weeks = new ArrayList<>();
        for (int i = 1; i <= weeksCount; i++) {
            weeks.add(Map.of(
                "week", i,
                "tasks", "<p>...........................................................................................................................</p>",
                "comment", "<p></p>",
                "sessions", 10,
                "hours", 40
            ));
        }
        content.put("weeks", weeks);
        content.put("scores", Map.of());
        content.put("comment", "<p></p>");
        content.put("suggestions", "<p></p>");
        content.put("paperSize", "A4");
        return content;
    }

    private String buildWordHtmlFallback(String title, ReportTemplateType type) {
        return """
            <html xmlns:o='urn:schemas-microsoft-com:office:office'
                  xmlns:w='urn:schemas-microsoft-com:office:word'
                  xmlns='http://www.w3.org/TR/REC-html40'>
            <head><meta http-equiv="Content-Type" content="text/html; charset=utf-8"></head>
            <body><h2 align="center">%s</h2><p>Trường Công nghệ Thông tin & Truyền thông - Đại học Cần Thơ</p></body></html>
            """.formatted(title);
    }
}
