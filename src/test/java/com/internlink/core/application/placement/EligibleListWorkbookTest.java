package com.internlink.core.application.placement;

import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class EligibleListWorkbookTest {

    @Test
    void titleAndFileNameFollowSchoolTemplate() {
        var term = InternshipTerm.builder().termName("Thực tập HK1").semester("HK1").academicYear("2026-2027").build();
        assertThat(EligibleListWorkbook.title(term, 2))
            .isEqualTo("DANH SÁCH SINH VIÊN ĐỦ ĐIỀU KIỆN TTTT HỌC KỲ 1, NĂM HỌC 2026-2027 (ĐỢT 2)");
        assertThat(EligibleListWorkbook.fileName(term, 2)).isEqualTo("DSSV_Du_Dieu_kien_TTTT_Hk1_2026-2027(dot2).xlsx");
        term.setSemester("HE");
        assertThat(EligibleListWorkbook.title(term, 1)).contains("HỌC KỲ HÈ");
    }

    @Test
    void workbookIsValidXlsxSortedByCourseThenStudentCodeAndEscapesText() throws Exception {
        var b = form("B2200002", "Trần <B>", "CT471", 3, "Cty A & B");
        var a = form("B2200001", "Nguyễn A", "CT471", 2, "Cty X");
        var c = form("B2100009", "Lê C", "CT518", 5, "Cty Y");
        byte[] bytes = EligibleListWorkbook.build("TIÊU ĐỀ", List.of(c, b, a));

        Map<String, String> parts = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry())
                parts.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertThat(parts).containsKeys("[Content_Types].xml", "xl/workbook.xml", "xl/styles.xml", "xl/worksheets/sheet1.xml");
        String sheet = parts.get("xl/worksheets/sheet1.xml");
        assertThat(sheet).contains("TIÊU ĐỀ", "Tên Công Ty (ghi chính xác)", "Trần &lt;B&gt;", "Cty A &amp; B",
            "CT471 - Thực tập thực tế - Kỹ thuật phần mềm", "CT518 - Thực tập doanh nghiệp - Kỹ thuật phần mềm");
        assertThat(sheet.indexOf("B2200001")).isLessThan(sheet.indexOf("B2200002"));
        assertThat(sheet.indexOf("B2200002")).isLessThan(sheet.indexOf("B2100009"));
    }

    private static StudentFoundResponse form(String code, String name, String course, int credits, String host) {
        return new StudentFoundResponse(null, null, null, host, null, null, null, null, null, null, null,
            "PUBLISHED", null, null, null, null, name, code, null, null, null, null, credits, null,
            null, null, null, null, null, null, null, null, null, null, "DI22V7A1", course, "Kỹ thuật phần mềm",
            null, false);
    }
}
