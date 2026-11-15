package com.internlink.core.application.announcement;

import com.internlink.core.application.announcement.AnnouncementDtos.*;
import com.internlink.core.application.meeting.MeetingAccessPolicy;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.announcement.LecturerAnnouncement;
import com.internlink.core.domain.announcement.LecturerAnnouncementRecipient;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaLecturerAnnouncementRecipientRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaLecturerAnnouncementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** GVHD gửi thông báo cho sinh viên mình hướng dẫn: thông báo trong hệ thống, kèm email nếu chọn. */
@Service
@RequiredArgsConstructor
public class AnnouncementService {
    public static final String NOTIFICATION_TYPE = "LECTURER_ANNOUNCEMENT";
    static final String STUDENT_URL = "/student/announcements";
    private static final int HISTORY_LIMIT = 100;

    private final JpaLecturerAnnouncementRepository announcements;
    private final JpaLecturerAnnouncementRecipientRepository recipients;
    private final JpaUserRepository users;
    private final MeetingAccessPolicy policy;
    private final NotificationService notifications;
    private final AuditLogService audit;
    private final SecurityGuard guard;

    @Transactional
    public AnnouncementView send(AnnouncementRequest request) {
        CustomUserDetail sender = guard.currentUser();
        Set<UUID> targetIds = new LinkedHashSet<>(request.recipientIds());
        policy.requireSupervisedAttendees(sender.getId(), targetIds);
        Map<UUID, User> people = users.findAllById(targetIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        if (people.size() != targetIds.size()) throw new ResourceNotFoundException("Không tìm thấy sinh viên");

        String title = request.title().strip();
        String body = request.body().strip();
        LecturerAnnouncement saved = announcements.saveAndFlush(LecturerAnnouncement.builder()
                .senderId(sender.getId())
                .title(title)
                .body(body)
                .sendEmail(request.sendEmail())
                .recipientCount(targetIds.size())
                .build());
        recipients.saveAll(targetIds.stream()
                .map(id -> new LecturerAnnouncementRecipient(saved.getId(), id))
                .toList());

        String notifTitle = truncate("Thông báo từ GVHD: " + title, 255);
        String message = "GVHD " + sender.getFullName() + " gửi:\n\n" + body;
        targetIds.forEach(id -> notifications.publishNotification(
                id, NOTIFICATION_TYPE, notifTitle, message, STUDENT_URL, request.sendEmail()));

        audit.logAction(sender.getId(), "ANNOUNCEMENT_SENT", "LecturerAnnouncement", saved.getId(), "SUCCESS",
                Map.of("recipients", targetIds.size(), "email", request.sendEmail()), null);
        List<Recipient> list = targetIds.stream()
                .map(id -> new Recipient(id, people.get(id).getFullName()))
                .toList();
        return view(saved, sender.getFullName(), list);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementView> listSent() {
        CustomUserDetail sender = guard.currentUser();
        List<LecturerAnnouncement> sent = announcements.findBySenderIdOrderByCreatedAtDesc(
                sender.getId(), PageRequest.of(0, HISTORY_LIMIT));
        if (sent.isEmpty()) return List.of();
        Map<UUID, List<UUID>> byAnnouncement = recipients
                .findByAnnouncementIdIn(sent.stream().map(LecturerAnnouncement::getId).toList()).stream()
                .collect(Collectors.groupingBy(LecturerAnnouncementRecipient::getAnnouncementId,
                        Collectors.mapping(LecturerAnnouncementRecipient::getUserId, Collectors.toList())));
        Set<UUID> everyone = byAnnouncement.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<UUID, String> names = users.findAllById(everyone).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return sent.stream()
                .map(a -> view(a, sender.getFullName(), byAnnouncement.getOrDefault(a.getId(), List.of()).stream()
                        .map(id -> new Recipient(id, names.getOrDefault(id, "")))
                        .sorted(Comparator.comparing(Recipient::fullName))
                        .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnnouncementView> listMine() {
        List<LecturerAnnouncement> mine = announcements.findForRecipient(
                guard.currentUser().getId(), PageRequest.of(0, HISTORY_LIMIT));
        Set<UUID> senderIds = mine.stream().map(LecturerAnnouncement::getSenderId).collect(Collectors.toSet());
        Map<UUID, String> senders = users.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
        return mine.stream()
                .map(a -> view(a, senders.getOrDefault(a.getSenderId(), "GVHD"), List.of()))
                .toList();
    }

    private static AnnouncementView view(LecturerAnnouncement a, String senderName, List<Recipient> list) {
        return new AnnouncementView(a.getId(), a.getTitle(), a.getBody(), senderName, a.isSendEmail(),
                a.getRecipientCount(), list, a.getCreatedAt());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
