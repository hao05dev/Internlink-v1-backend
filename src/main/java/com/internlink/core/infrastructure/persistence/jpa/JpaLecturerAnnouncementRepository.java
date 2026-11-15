package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.announcement.LecturerAnnouncement;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaLecturerAnnouncementRepository extends JpaRepository<LecturerAnnouncement, UUID> {
    List<LecturerAnnouncement> findBySenderIdOrderByCreatedAtDesc(UUID senderId, Pageable page);

    @Query("SELECT a FROM LecturerAnnouncement a WHERE a.id IN "
            + "(SELECT r.announcementId FROM LecturerAnnouncementRecipient r WHERE r.userId = :userId) "
            + "ORDER BY a.createdAt DESC")
    List<LecturerAnnouncement> findForRecipient(@Param("userId") UUID userId, Pageable page);
}
