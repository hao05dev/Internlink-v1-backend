package com.internlink.core.application.ai_matching.impl;

import com.internlink.core.domain.organization.InternshipTerm;
import java.util.HashMap;
import java.util.Map;

/** Term entry of the AI workspace options, shaped for the shared "Niên khóa → Học kỳ" filter. */
final class AiTermOptions {
    private AiTermOptions() {}

    static Map<String, Object> of(InternshipTerm t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getTermName());
        m.put("termName", t.getTermName());
        if (t.getCode() != null) m.put("code", t.getCode());
        if (t.getAcademicYear() != null) m.put("academicYear", t.getAcademicYear());
        if (t.getSemester() != null) m.put("semester", t.getSemester());
        if (t.getStatus() != null) m.put("status", t.getStatus().name());
        return m;
    }
}
