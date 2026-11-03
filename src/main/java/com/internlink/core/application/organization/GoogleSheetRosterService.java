package com.internlink.core.application.organization;

import com.internlink.core.domain.placement.InternshipDurationRule;
import com.fasterxml.jackson.databind.JsonNode;
import com.internlink.core.infrastructure.integration.google.GoogleWorkspaceClient;
import com.internlink.core.infrastructure.persistence.jpa.JpaAcademicProgramRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GoogleSheetRosterService {
    private final GoogleWorkspaceClient google;
    private final JpaAcademicProgramRepository programs;
    private final StudentRosterService rosterService;
    private final JpaInternshipTermRepository terms;
    private final JpaUserRepository users;
    private final SecurityGuard securityGuard;

    @Transactional
    public List<StudentRosterResponse> importSheet(UUID termId, String spreadsheetId, String range) {
        var term = terms.findById(termId).orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        TermGuard.requireNotClosed(term);
        var actor = users.findById(securityGuard.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(actor, term.getDepartment().getId()));
        if (spreadsheetId == null || !spreadsheetId.matches("[A-Za-z0-9_-]{20,}"))
            throw new BadRequestException("Spreadsheet ID không hợp lệ");
        if (range == null || range.isBlank() || range.length() > 100)
            throw new BadRequestException("Cần tên trang tính hoặc vùng dữ liệu hợp lệ");
        JsonNode result = google.json("GET", URI.create("https://sheets.googleapis.com/v4/spreadsheets/"
            + spreadsheetId + "/values/" + GoogleWorkspaceClient.encode(range)),
            GoogleWorkspaceClient.SHEETS_READ, null, null, null);
        JsonNode rows = result.path("values");
        if (!rows.isArray() || rows.size() < 2) throw new BadRequestException("Google Sheet chưa có dữ liệu sinh viên");
        if (rows.size() > 1001) throw new BadRequestException("Mỗi lần chỉ import tối đa 1000 sinh viên");
        Map<String, Integer> headers = new HashMap<>();
        for (int i = 0; i < rows.get(0).size(); i++)
            headers.put(rows.get(0).get(i).asText().trim().toLowerCase(Locale.ROOT), i);
        for (String required : List.of("studentcode", "fullname", "officialemail", "programcode", "academicyear", "internshipcoursecode", "gpa"))
            if (!headers.containsKey(required)) throw new BadRequestException("Google Sheet thiếu cột " + required);
        List<StudentRosterImportItem> items = new ArrayList<>();
        Set<String> studentCodes = new HashSet<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            JsonNode row = rows.get(rowIndex);
            String code = cell(row, headers, "studentcode").toUpperCase(Locale.ROOT);
            if (code.isBlank()) continue;
            if (!studentCodes.add(code)) throw new BadRequestException("MSSV trùng ở dòng " + (rowIndex + 1));
            String programCode = cell(row, headers, "programcode").toUpperCase(Locale.ROOT);
            int lineNumber = rowIndex + 1;
            var program = programs.findByCode(programCode)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy ngành " + programCode + " ở dòng " + lineNumber));
            items.add(StudentRosterImportItem.builder()
                .programId(program.getId()).studentCode(code)
                .fullName(cell(row, headers, "fullname"))
                .officialEmail(cell(row, headers, "officialemail"))
                .academicYear(cell(row, headers, "academicyear"))
                .internshipCourseCode(cell(row, headers, "internshipcoursecode"))
                .internshipCredits(parseCredits(cell(row, headers, "internshipcredits"), lineNumber))
                .gpa(parseGpa(cell(row, headers, "gpa"), lineNumber))
                .classCode(cell(row, headers, "classcode"))
                .phoneNumber(cell(row, headers, "phonenumber"))
                .lecturerCode(cell(row, headers, "lecturercode"))
                .lecturerName(cell(row, headers, "lecturername"))
                .lecturerEmail(cell(row, headers, "lectureremail"))
                .build());
        }
        if (items.isEmpty()) throw new BadRequestException("Google Sheet không có sinh viên hợp lệ");
        return rosterService.importRosterList(termId, items);
    }

    private BigDecimal parseGpa(String raw, int lineNumber) {
        if (raw.isBlank()) throw new BadRequestException("Thiếu điểm GPA ở dòng " + lineNumber);
        try {
            return new BigDecimal(raw.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Điểm GPA không hợp lệ ở dòng " + lineNumber);
        }
    }

    private Integer parseCredits(String raw, int lineNumber) {
        if (raw.isBlank()) return null;
        try {
            int credits = Integer.parseInt(raw);
            InternshipDurationRule.requireValidCredits(credits);
            return credits;
        } catch (NumberFormatException | BadRequestException e) {
            throw new BadRequestException("Số tín chỉ thực tập không hợp lệ ở dòng " + lineNumber + " (chỉ nhận 2, 3 hoặc 5)");
        }
    }

    private String cell(JsonNode row, Map<String, Integer> headers, String header) {
        Integer index = headers.get(header);
        return index == null || index >= row.size() ? "" : row.get(index).asText().trim();
    }
}
