package com.internlink.core.application.placement;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPlacementOfferRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.shared.enums.OfferStatus;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service kiểm tra điều kiện học vụ và tính hợp lệ khi sinh viên tham gia thực tập hoặc nhận offer.
 * Đảm bảo luật: Mỗi sinh viên chỉ có tối đa 1 placement hoạt động trong mỗi kỳ thực tập.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementEligibilityService {

    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaStudentRosterRepository rosterRepository;
    private final JpaPlacementOfferRepository offerRepository;

    /**
     * Yêu cầu sinh viên chưa có bất kỳ placement nào đang hoạt động (không phải TERMINATED hay TRANSFERRED) trong kỳ.
     */
    public void requireNoLivePlacement(UUID studentId, UUID termId) {
        if (studentId == null || termId == null) {
            return;
        }
        boolean hasLivePlacement = placementRepository.findByTermIdAndStudentId(termId, studentId).stream()
                .anyMatch(p -> p.getStatus() != PlacementStatus.TERMINATED 
                            && p.getStatus() != PlacementStatus.TRANSFERRED);
        if (hasLivePlacement) {
            throw new BadRequestException("Sinh viên đã có một vị trí thực tập đang hoạt động trong kỳ này");
        }
    }

    /**
     * Yêu cầu sinh viên phải có tên trong danh sách đăng ký thực tập (Roster) của kỳ.
     */
    public StudentRoster requireEligibleRoster(UUID studentId, UUID termId) {
        if (termId == null) {
            throw new BadRequestException("Kỳ thực tập không hợp lệ để tham gia thực tập");
        }
        StudentRoster roster = rosterRepository.findByTermIdAndClaimedUserId(termId, studentId)
                .orElseThrow(() -> new BadRequestException("Sinh viên chưa có trong danh sách kỳ thực tập này"));
        return roster;
    }

    /**
     * Yêu cầu sinh viên chưa chấp nhận (ACCEPTED) lời mời thực tập nào khác trong kỳ.
     */
    public void requireNoAcceptedOffer(UUID studentId, UUID termId, UUID excludeOfferId) {
        if (studentId == null || termId == null) {
            return;
        }
        boolean hasAcceptedOffer = offerRepository.findAll().stream()
                .filter(o -> o.getStatus() == OfferStatus.ACCEPTED)
                .filter(o -> o.getApplication() != null && o.getApplication().getStudent().getId().equals(studentId))
                .filter(o -> o.getApplication().getJob() != null 
                          && o.getApplication().getJob().getTerm() != null 
                          && o.getApplication().getJob().getTerm().getId().equals(termId))
                .anyMatch(o -> excludeOfferId == null || !o.getId().equals(excludeOfferId));

        if (hasAcceptedOffer) {
            throw new BadRequestException("Sinh viên đã chấp nhận một đề nghị thực tập khác trong kỳ này");
        }
    }

    /**
     * GVHD cho lần thực tập mới: giảng viên được chọn lúc kích hoạt, nếu không có thì dùng GVHD đã gán
     * trên danh sách đăng ký học phần.
     */
    public User resolveLecturer(User chosen, StudentRoster roster) {
        User lecturer = chosen != null ? chosen : roster != null ? roster.getAssignedLecturer() : null;
        if (lecturer == null) {
            throw new BadRequestException("Sinh viên chưa có GVHD trên danh sách học phần, cần phân công giảng viên hướng dẫn");
        }
        return lecturer;
    }

    /** Giữ GVHD trên danh sách học phần trùng với GVHD của lần thực tập. */
    public void syncRosterLecturer(UUID studentId, UUID termId, User lecturer) {
        if (studentId == null || termId == null || lecturer == null) {
            return;
        }
        rosterRepository.findByTermIdAndClaimedUserId(termId, studentId).ifPresent(roster -> {
            if (roster.getAssignedLecturer() != null && roster.getAssignedLecturer().getId().equals(lecturer.getId())) {
                return;
            }
            if (lecturer.getEmail() == null || !lecturer.getEmail().equalsIgnoreCase(roster.getLecturerEmail())) {
                roster.setLecturerCode(null);
            }
            roster.setAssignedLecturer(lecturer);
            roster.setLecturerName(lecturer.getFullName());
            roster.setLecturerEmail(lecturer.getEmail());
            rosterRepository.save(roster);
        });
    }

    /**
     * Kiểm tra tính hợp lệ của giảng viên hướng dẫn (phải ACTIVE và thuộc đúng Khoa).
     */
    public void requireActiveLecturer(User lecturer, UUID departmentId) {
        if (lecturer == null || lecturer.getRole() != UserRole.LECTURER) {
            throw new BadRequestException("Giảng viên không hợp lệ hoặc không có vai trò LECTURER");
        }
        if (!Boolean.TRUE.equals(lecturer.getIsActive())) {
            throw new BadRequestException("Tài khoản giảng viên hiện đang bị khóa hoặc không hoạt động");
        }
        if (departmentId != null) {
            if (lecturer.getDepartment() == null || !lecturer.getDepartment().getId().equals(departmentId)) {
                throw new BadRequestException("Giảng viên không thuộc đơn vị quản lý kỳ thực tập");
            }
        }
    }
}
