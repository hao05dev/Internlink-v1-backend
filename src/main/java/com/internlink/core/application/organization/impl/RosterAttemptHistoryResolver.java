package com.internlink.core.application.organization.impl;

import com.internlink.core.domain.evaluation.FinalResult;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaFinalResultRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.presentation.organization.dto.response.RosterPriorAttemptResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.ResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nhận ra sinh viên thực tập lại: cùng MSSV đã có tên trong danh sách học phần của một kỳ trước (cùng khoa).
 * Khớp theo MSSV chứ không theo email, để sinh viên đổi email vẫn được nhận ra.
 */
@Component
@RequiredArgsConstructor
public class RosterAttemptHistoryResolver {

    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String TERMINATED = "TERMINATED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String NO_PLACEMENT = "NO_PLACEMENT";

    private static final Set<PlacementStatus> LIVE =
            Set.of(PlacementStatus.PREPARING, PlacementStatus.ACTIVE, PlacementStatus.PAUSED);

    private final JpaStudentRosterRepository rosterRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaFinalResultRepository finalResultRepository;

    /** Gắn attemptNumber và priorAttempts vào các response của kỳ {@code term}. */
    public void enrich(InternshipTerm term, Collection<StudentRoster> rosters, List<StudentRosterResponse> responses) {
        Map<String, List<RosterPriorAttemptResponse>> history = resolve(term, rosters);
        for (StudentRosterResponse response : responses) {
            List<RosterPriorAttemptResponse> prior = history.getOrDefault(response.getStudentCode(), List.of());
            response.setPriorAttempts(prior);
            response.setAttemptNumber(prior.size() + 1);
        }
    }

    /** MSSV → các kỳ trước (cũ nhất trước); MSSV lần đầu thực tập không có trong map. */
    public Map<String, List<RosterPriorAttemptResponse>> resolve(InternshipTerm term, Collection<StudentRoster> rosters) {
        Map<String, StudentRoster> current = rosters.stream()
                .collect(Collectors.toMap(StudentRoster::getStudentCode, r -> r, (a, b) -> a));
        if (current.isEmpty()) return Map.of();

        List<StudentRoster> earlier = rosterRepository
                .findInOtherTermsByStudentCodes(current.keySet(), term.getDepartment().getId(), term.getId())
                .stream()
                .filter(r -> isEarlier(r.getTerm(), term))
                .sorted(Comparator.comparing(StudentRoster::getTerm, RosterAttemptHistoryResolver::compareTerms))
                .toList();
        if (earlier.isEmpty()) return Map.of();

        Map<UUID, Map<UUID, List<InternshipPlacement>>> placements = loadPlacements(earlier, current);
        Map<UUID, FinalResult> results = loadResults(placements);

        Map<String, List<RosterPriorAttemptResponse>> history = new HashMap<>();
        for (StudentRoster prior : earlier) {
            UUID studentId = studentIdOf(prior, current.get(prior.getStudentCode()));
            List<InternshipPlacement> inTerm = studentId == null ? List.of()
                    : placements.getOrDefault(studentId, Map.of()).getOrDefault(prior.getTerm().getId(), List.of());
            history.computeIfAbsent(prior.getStudentCode(), k -> new ArrayList<>())
                    .add(new RosterPriorAttemptResponse(prior.getTerm().getId(), prior.getTerm().getTermName(),
                            outcomeOf(inTerm, results)));
        }
        return history;
    }

    /** Kết quả của một kỳ: đạt/rớt ưu tiên nhất, rồi đang thực tập, rồi đã dừng. */
    static String outcomeOf(List<InternshipPlacement> placements, Map<UUID, FinalResult> results) {
        if (placements.isEmpty()) return NO_PLACEMENT;
        for (InternshipPlacement placement : placements) {
            if (placement.getStatus() != PlacementStatus.COMPLETED) continue;
            FinalResult result = results.get(placement.getId());
            boolean failed = result != null && (result.getResultStatus() == ResultStatus.FAILED
                    || result.getResultStatus() == ResultStatus.NOT_COMPLETED);
            return failed ? FAILED : PASSED;
        }
        if (placements.stream().anyMatch(p -> LIVE.contains(p.getStatus()))) return IN_PROGRESS;
        return TERMINATED;
    }

    static boolean isEarlier(InternshipTerm other, InternshipTerm current) {
        return compareTerms(other, current) < 0;
    }

    private static int compareTerms(InternshipTerm a, InternshipTerm b) {
        if (a.getStartDate() != null && b.getStartDate() != null && !a.getStartDate().equals(b.getStartDate())) {
            return a.getStartDate().compareTo(b.getStartDate());
        }
        if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
            return a.getCreatedAt().compareTo(b.getCreatedAt());
        }
        return 0;
    }

    /** Dòng roster cũ có thể chưa cấp tài khoản; khi đó dùng tài khoản của dòng hiện tại (cùng MSSV). */
    private static UUID studentIdOf(StudentRoster prior, StudentRoster current) {
        if (prior.getClaimedUser() != null) return prior.getClaimedUser().getId();
        return current != null && current.getClaimedUser() != null ? current.getClaimedUser().getId() : null;
    }

    private Map<UUID, Map<UUID, List<InternshipPlacement>>> loadPlacements(List<StudentRoster> earlier,
                                                                         Map<String, StudentRoster> current) {
        Set<UUID> studentIds = new HashSet<>();
        Set<UUID> termIds = new HashSet<>();
        for (StudentRoster prior : earlier) {
            termIds.add(prior.getTerm().getId());
            UUID studentId = studentIdOf(prior, current.get(prior.getStudentCode()));
            if (studentId != null) studentIds.add(studentId);
        }
        if (studentIds.isEmpty()) return Map.of();
        return placementRepository.findByStudentIdsAndTermIds(studentIds, termIds).stream()
                .collect(Collectors.groupingBy(p -> p.getStudent().getId(),
                        Collectors.groupingBy(p -> p.getTerm().getId())));
    }

    private Map<UUID, FinalResult> loadResults(Map<UUID, Map<UUID, List<InternshipPlacement>>> placements) {
        List<UUID> completed = placements.values().stream()
                .flatMap(byTerm -> byTerm.values().stream())
                .flatMap(List::stream)
                .filter(p -> p.getStatus() == PlacementStatus.COMPLETED)
                .map(InternshipPlacement::getId)
                .toList();
        if (completed.isEmpty()) return Map.of();
        return finalResultRepository.findByPlacementIdIn(completed).stream()
                .filter(r -> r.getPlacement() != null)
                .collect(Collectors.toMap(r -> r.getPlacement().getId(), r -> r, (a, b) -> a));
    }
}
