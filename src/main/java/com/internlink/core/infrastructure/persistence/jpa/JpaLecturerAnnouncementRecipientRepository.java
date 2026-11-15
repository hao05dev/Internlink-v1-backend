package com.internlink.core.infrastructure.persistence.jpa;

import com.internlink.core.domain.announcement.LecturerAnnouncementRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface JpaLecturerAnnouncementRecipientRepository
        extends JpaRepository<LecturerAnnouncementRecipient, LecturerAnnouncementRecipient.Key> {
    List<LecturerAnnouncementRecipient> findByAnnouncementIdIn(Collection<UUID> announcementIds);
}
