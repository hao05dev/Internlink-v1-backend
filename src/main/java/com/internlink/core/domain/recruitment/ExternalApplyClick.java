package com.internlink.core.domain.recruitment;

import com.internlink.core.domain.auth.User;
import com.internlink.core.shared.domain.BaseEntity;

import jakarta.persistence.*;

import lombok.*;

import java.time.OffsetDateTime;

/** Lượt SV bấm "Ứng tuyển qua kênh của DN" trên tin EXTERNAL; một dòng mỗi SV mỗi tin. */
@Entity
@Table(name = "external_apply_clicks", uniqueConstraints = {
    @UniqueConstraint(name = "uq_external_apply_clicks_student_job", columnNames = {"student_id", "job_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalApplyClick extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private JobPosition job;

    @Builder.Default
    @Column(name = "click_count", nullable = false)
    private Integer clickCount = 1;

    @Column(name = "last_clicked_at", nullable = false)
    private OffsetDateTime lastClickedAt;
}
