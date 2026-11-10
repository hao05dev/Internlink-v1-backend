package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.organization.StudentRoster;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaStudentRosterRepository extends JpaRepository<StudentRoster, UUID> {
    Optional<StudentRoster> findByTermIdAndStudentCode(UUID termId, String studentCode);
    Optional<StudentRoster> findByTermIdAndOfficialEmail(UUID termId, String officialEmail);
    List<StudentRoster> findByTermId(UUID termId);
    /** Phần danh sách học phần của một khoa trong kỳ cấp Trường (khoa = khoa quản lý ngành). */
    List<StudentRoster> findByTermIdAndProgramDepartmentId(UUID termId, UUID departmentId);
    List<StudentRoster> findByTermIdAndProgramDepartmentIdOrderByStudentCodeAsc(UUID termId, UUID departmentId);
    List<StudentRoster> findByTermIdOrderByStudentCodeAsc(UUID termId);
    boolean existsByTermIdAndStudentCode(UUID termId, String studentCode);
    boolean existsByProgram_Id(UUID programId);

    @Query("SELECT r FROM StudentRoster r WHERE r.term.id = :termId AND r.claimedUser.id = :userId")
    Optional<StudentRoster> findByTermIdAndClaimedUserId(@Param("termId") UUID termId,
                                                          @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM StudentRoster r WHERE r.term.id = :termId AND r.claimedUser.id = :userId")
    Optional<StudentRoster> findByTermIdAndClaimedUserIdForUpdate(@Param("termId") UUID termId,
                                                                 @Param("userId") UUID userId);

    /** [lecturerId, count] of roster rows supervised by each lecturer in the term. */
    @Query("SELECT r.assignedLecturer.id, COUNT(r) FROM StudentRoster r "
            + "WHERE r.term.id = :termId AND r.assignedLecturer IS NOT NULL GROUP BY r.assignedLecturer.id")
    List<Object[]> countByAssignedLecturerInTerm(@Param("termId") UUID termId);

    /** Links imported rows that named this lecturer by email before the lecturer had an account. */
    @Modifying
    @Query("UPDATE StudentRoster r SET r.assignedLecturer = :lecturer "
            + "WHERE r.assignedLecturer IS NULL AND LOWER(r.lecturerEmail) = LOWER(:email) "
            + "AND r.program.department.id = :departmentId")
    int linkUnassignedByLecturerEmail(@Param("lecturer") com.internlink.core.domain.auth.User lecturer,
                                      @Param("email") String email,
                                      @Param("departmentId") UUID departmentId);

    /** Dòng roster của các MSSV này ở những kỳ khác (MSSV duy nhất toàn Trường), để nhận ra SV thực tập lại. */
    @Query("SELECT r FROM StudentRoster r JOIN FETCH r.term t LEFT JOIN FETCH r.claimedUser "
            + "WHERE r.studentCode IN :codes AND t.id <> :termId")
    List<StudentRoster> findInOtherTermsByStudentCodes(@Param("codes") java.util.Collection<String> codes,
                                                       @Param("termId") UUID termId);

    Optional<StudentRoster> findFirstByClaimedUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<StudentRoster> findFirstByOfficialEmailOrderByCreatedAtDesc(String officialEmail);
}
