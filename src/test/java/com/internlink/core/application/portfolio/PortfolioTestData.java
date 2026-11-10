package com.internlink.core.application.portfolio;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.*;
import com.internlink.core.domain.placement.*;
import com.internlink.core.shared.enums.*;
import java.time.LocalDate;
import java.util.*;

final class PortfolioTestData {
    static User user(UserRole role){var u=User.builder().role(role).fullName(role.name()).email(role+"@example.test").passwordHash("unused").build();u.setId(UUID.randomUUID());return u;}
    static InternshipPlacement placement(){
        var department=Department.builder().name("Công nghệ thông tin").code("IT").build();department.setId(UUID.randomUUID());
        var term=InternshipTerm.builder().termName("Thực tập 2026").build();term.setId(UUID.randomUUID());
        var p=InternshipPlacement.builder().student(user(UserRole.STUDENT)).mentor(user(UserRole.COMPANY_MENTOR)).lecturer(user(UserRole.LECTURER))
            .term(term).startDate(LocalDate.of(2026,1,5)).endDate(LocalDate.of(2026,3,29)).status(PlacementStatus.ACTIVE).source("PARTNER_PORTAL").workSchedule(Map.of()).build();p.setId(UUID.randomUUID());return p;
    }
    static PortfolioForm form(InternshipPlacement p,String kind){var f=new PortfolioForm();f.setId(UUID.randomUUID());f.setPlacement(p);f.setKind(kind);return f;}
}
