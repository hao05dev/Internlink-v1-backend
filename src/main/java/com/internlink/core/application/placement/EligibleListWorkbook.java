package com.internlink.core.application.placement;

import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * "DSSV đủ điều kiện TTTT" theo mẫu Văn phòng Trường: dòng tiêu đề, dòng trống, 6 cột
 * (Stt, Mã SV, Họ và tên, Mã lớp, Tên học phần, Tên công ty), sắp theo mã học phần rồi MSSV.
 * Ghi thẳng định dạng .xlsx (Office Open XML) để không phải kéo thêm thư viện; mọi ô là chuỗi
 * inline nên không có công thức nào được thực thi khi mở tệp.
 */
public final class EligibleListWorkbook {
    public static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final List<String> HEADERS =
        List.of("Stt", "Mã SV", "Họ và tên", "Mã lớp", "Tên học phần", "Tên Công Ty (ghi chính xác)");
    private static final int[] WIDTHS = {6, 12, 26, 12, 46, 50};
    private static final Set<String> SUMMER = Set.of("HE", "HÈ", "SUMMER", "3");

    private EligibleListWorkbook() {}

    /** "DANH SÁCH SINH VIÊN ĐỦ ĐIỀU KIỆN TTTT HỌC KỲ 1, NĂM HỌC 2026-2027 (ĐỢT 1)" */
    public static String title(InternshipTerm term, int batch) {
        String sem = semesterLabel(term.getSemester());
        String period = !sem.isEmpty() && term.getAcademicYear() != null
            ? "HỌC KỲ " + sem + ", NĂM HỌC " + term.getAcademicYear()
            : Objects.toString(term.getTermName(), "").toUpperCase(Locale.ROOT);
        return ("DANH SÁCH SINH VIÊN ĐỦ ĐIỀU KIỆN TTTT " + period + " (ĐỢT " + batch + ")")
            .replaceAll("\\s+", " ").trim();
    }

    /** DSSV_Du_Dieu_kien_TTTT_Hk1_2026-2027(dot1).xlsx */
    public static String fileName(InternshipTerm term, int batch) {
        String sem = semesterLabel(term.getSemester());
        String period = !sem.isEmpty() && term.getAcademicYear() != null
            ? "Hk" + ("HÈ".equals(sem) ? "He" : sem) + "_" + term.getAcademicYear()
            : Objects.toString(term.getTermName(), "KyThucTap");
        return "DSSV_Du_Dieu_kien_TTTT_" + period.replaceAll("[^a-zA-Z0-9_-]", "_") + "(dot" + batch + ").xlsx";
    }

    /** "HK1"/"1" → "1"; "HE"/"HK3"/"SUMMER" → "HÈ". */
    static String semesterLabel(String semester) {
        String s = Objects.toString(semester, "").trim().toUpperCase(Locale.ROOT).replaceFirst("^HK\\s*", "");
        return SUMMER.contains(s) ? "HÈ" : s;
    }

    /** "CT471 - Thực tập thực tế - Kỹ thuật phần mềm": 5 tín chỉ là Thực tập doanh nghiệp, 2-3 tín chỉ là TTTT. */
    static String courseLabel(StudentFoundResponse f) {
        String name = f.internshipCredits() != null && f.internshipCredits() >= 5
            ? "Thực tập doanh nghiệp" : "Thực tập thực tế";
        return Stream.of(f.courseCode(), name, f.programName() != null ? f.programName() : f.departmentName())
            .filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" - "));
    }

    static List<StudentFoundResponse> sorted(List<StudentFoundResponse> forms) {
        return forms.stream()
            .sorted(Comparator.comparing((StudentFoundResponse f) -> Objects.toString(f.courseCode(), ""))
                .thenComparing(f -> Objects.toString(f.studentCode(), "")))
            .toList();
    }

    public static byte[] build(String title, List<StudentFoundResponse> forms) {
        var rows = new StringBuilder();
        rows.append(row(1, List.of(title), 1)).append("<row r=\"2\"/>").append(row(3, HEADERS, 2));
        int r = 4;
        int stt = 1;
        for (var f : sorted(forms))
            rows.append(row(r++, List.of(String.valueOf(stt++), text(f.studentCode()), text(f.studentName()),
                text(f.classCode()), courseLabel(f), text(f.hostName())), 3));

        var cols = new StringBuilder("<cols>");
        for (int i = 0; i < WIDTHS.length; i++)
            cols.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                .append("\" width=\"").append(WIDTHS[i]).append("\" customWidth=\"1\"/>");
        cols.append("</cols>");

        String sheet = XML + "<worksheet xmlns=\"" + MAIN_NS + "\">" + cols + "<sheetData>" + rows
            + "</sheetData><mergeCells count=\"1\"><mergeCell ref=\"A1:F1\"/></mergeCells></worksheet>";

        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", WORKBOOK);
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    private static String row(int r, List<String> values, int style) {
        var sb = new StringBuilder("<row r=\"").append(r).append("\">");
        for (int c = 0; c < values.size(); c++)
            sb.append("<c r=\"").append((char) ('A' + c)).append(r).append("\" s=\"").append(style)
                .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(escape(values.get(c)))
                .append("</t></is></c>");
        return sb.append("</row>").toString();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** Escape XML và bỏ ký tự điều khiển không hợp lệ trong XML 1.0. */
    static String escape(String value) {
        var sb = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> {
            switch (cp) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp != 0xFFFE && cp != 0xFFFF))
                        sb.appendCodePoint(cp);
                }
            }
        });
        return sb.toString();
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>";
    private static final String MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String CONTENT_TYPES = XML
        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        + "</Types>";
    private static final String ROOT_RELS = XML
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>";
    private static final String WORKBOOK = XML
        + "<workbook xmlns=\"" + MAIN_NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
        + "<sheets><sheet name=\"DSSV\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    private static final String WORKBOOK_RELS = XML
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        + "</Relationships>";
    /** Kiểu ô: 0 mặc định, 1 tiêu đề (đậm, căn giữa), 2 tiêu đề cột (đậm, viền), 3 dữ liệu (viền). */
    private static final String STYLES = XML
        + "<styleSheet xmlns=\"" + MAIN_NS + "\">"
        + "<fonts count=\"3\"><font><sz val=\"11\"/><name val=\"Times New Roman\"/></font>"
        + "<font><b/><sz val=\"13\"/><name val=\"Times New Roman\"/></font>"
        + "<font><b/><sz val=\"11\"/><name val=\"Times New Roman\"/></font></fonts>"
        + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
        + "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>"
        + "<border><left style=\"thin\"/><right style=\"thin\"/><top style=\"thin\"/><bottom style=\"thin\"/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>"
        + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyBorder=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>"
        + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
        + "</styleSheet>";
}
