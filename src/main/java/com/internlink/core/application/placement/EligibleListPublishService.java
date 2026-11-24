package com.internlink.core.application.placement;

import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.placement.AcceptanceFormStatus;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Văn phòng Trường CNTT-TT công bố Danh sách SV đủ điều kiện TTTT theo đợt:
 * mọi phiếu Khoa đã duyệt (APPROVED) của kỳ chuyển PUBLISHED, sinh tệp Excel từ DB, đăng lên Tin tức,
 * rồi báo SV (trong app + email) đến sảnh VP Trường nhận giấy tờ.
 * Tệp xem trước và tệp đăng lên Tin tức sinh bởi cùng một hàm nên nội dung trùng khớp.
 */
@Service
@RequiredArgsConstructor
public class EligibleListPublishService {
    private final JpaStudentFoundApplicationRepository applications;
    private final JpaInternshipTermRepository terms;
    private final AcceptanceFormLookup lookup;
    private final AcceptanceFormMapper mapper;
    private final AcceptanceFormNotifier notifier;
    private final EligibleListNewsPublisher news;

    public record Download(byte[] bytes, String fileName) {}

    public record PublishResult(int publishedCount, int batch, String newsSlug, String message) {}

    /** Tình trạng công bố của kỳ cho màn hình VP Trường. */
    public record Summary(int pendingCount, int nextBatch, List<EligibleListNewsPublisher.Batch> batches, String newsSlug) {}

    @Transactional(readOnly = true)
    public Summary summary(UUID termId) {
        var term = requireTerm(termId);
        requireOffice();
        var batches = batches(termId);
        return new Summary(applications.findByTermIdAndStatus(termId, AcceptanceFormStatus.APPROVED).size(),
            batches.size() + 1, batches, news.existing(term)
                .filter(p -> "PUBLISHED".equals(p.getStatus())).map(p -> p.getSlug()).orElse(null));
    }

    /**
     * Tệp Excel của một đợt: {@code batch} = null là đợt sắp công bố (các phiếu đã duyệt, chưa công bố),
     * dùng để VP Trường kiểm tra trước khi bấm Công bố.
     */
    @Transactional(readOnly = true)
    public Download workbook(UUID termId, Integer batch) {
        var term = requireTerm(termId);
        requireOffice();
        var existing = batchStamps(termId);
        List<StudentFoundApplication> forms;
        int number;
        if (batch == null) {
            forms = applications.findByTermIdAndStatus(termId, AcceptanceFormStatus.APPROVED);
            if (forms.isEmpty()) throw new BadRequestException("Không có phiếu đã duyệt nào chờ công bố");
            number = existing.size() + 1;
        } else {
            if (batch < 1 || batch > existing.size()) throw new BadRequestException("Đợt công bố không tồn tại");
            var stamp = existing.get(batch - 1);
            forms = published(termId).stream().filter(a -> stamp.equals(a.getPublishedAt().toInstant())).toList();
            number = batch;
        }
        return new Download(toWorkbook(term, number, forms), EligibleListWorkbook.fileName(term, number));
    }

    @Transactional
    public PublishResult publish(UUID termId, String note) {
        var term = requireTerm(termId);
        TermGuard.requireNotClosed(term);
        var publisher = requireOffice();
        var approved = applications.findByTermIdAndStatus(termId, AcceptanceFormStatus.APPROVED);
        if (approved.isEmpty()) throw new BadRequestException("Không có phiếu đã duyệt nào chờ công bố");
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000)
            throw new BadRequestException("Thông báo kèm theo tối đa 1000 ký tự");

        int batch = batchStamps(termId).size() + 1;
        byte[] workbook = toWorkbook(term, batch, approved);
        // Cùng một mốc cho cả đợt (làm tròn micro giây như PostgreSQL) để nhóm đợt ổn định sau khi lưu.
        var now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        for (var app : approved) {
            app.setStatus(AcceptanceFormStatus.PUBLISHED);
            app.setPublishedAt(now);
            app.setPublishedBy(publisher);
        }
        applications.saveAll(approved);

        var batches = new ArrayList<>(batches(termId));
        batches.removeIf(b -> b.number() == batch);
        batches.add(new EligibleListNewsPublisher.Batch(batch, now, approved.size()));
        String slug = news.publish(term, publisher, batches, workbook,
            EligibleListWorkbook.fileName(term, batch), cleanNote);
        terms.save(term);

        String link = "/career-guide/" + slug;
        approved.forEach(app -> notifier.published(app, batch, link, cleanNote));
        Map<UUID, Long> byDepartment = approved.stream().map(DepartmentScope::idOf).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        notifier.listPublished(term, batch, byDepartment, link);
        return new PublishResult(approved.size(), batch, slug,
            "Đã công bố đợt " + batch + " gồm " + approved.size() + " sinh viên, đăng Tin tức và gửi email cho sinh viên");
    }

    private byte[] toWorkbook(InternshipTerm term, int batch, List<StudentFoundApplication> forms) {
        return EligibleListWorkbook.build(EligibleListWorkbook.title(term, batch),
            forms.stream().map(mapper::toResponse).toList());
    }

    private List<StudentFoundApplication> published(UUID termId) {
        return applications.findByTermIdAndStatus(termId, AcceptanceFormStatus.PUBLISHED).stream()
            .filter(a -> a.getPublishedAt() != null).toList();
    }

    /** Mỗi mốc publishedAt khác nhau là một đợt, sắp tăng dần. */
    private List<Instant> batchStamps(UUID termId) {
        return published(termId).stream().map(a -> a.getPublishedAt().toInstant()).distinct().sorted().toList();
    }

    private List<EligibleListNewsPublisher.Batch> batches(UUID termId) {
        var counts = published(termId).stream()
            .collect(Collectors.groupingBy(a -> a.getPublishedAt().toInstant(), TreeMap::new, Collectors.counting()));
        List<EligibleListNewsPublisher.Batch> out = new ArrayList<>();
        int n = 1;
        for (var e : counts.entrySet())
            out.add(new EligibleListNewsPublisher.Batch(n++,
                OffsetDateTime.ofInstant(e.getKey(), java.time.ZoneOffset.UTC), e.getValue().intValue()));
        return out;
    }

    private InternshipTerm requireTerm(UUID termId) {
        return terms.findById(termId).orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
    }

    private com.internlink.core.domain.auth.User requireOffice() {
        var actor = lookup.actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor));
        return actor;
    }
}
