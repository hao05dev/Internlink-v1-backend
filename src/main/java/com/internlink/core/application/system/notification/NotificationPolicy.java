package com.internlink.core.application.system.notification;
import com.internlink.core.domain.system.NotificationPreference;
import java.util.Set;

public final class NotificationPolicy {
    public enum Category { WORKFLOW, REMINDER, ANNOUNCEMENT, SYSTEM }
    private NotificationPolicy() {}
    public static Category category(String type) {
        if (type.endsWith("_REMINDER") || type.endsWith("_DEADLINE")) return Category.REMINDER;
        if (Set.of("NEW_JOB_POSTED", "TERM_OPENED", "GENERAL_ANNOUNCEMENT", "TERM_PLAN_PUBLISHED").contains(type)) return Category.ANNOUNCEMENT;
        if (type.startsWith("SYSTEM_") || type.startsWith("SECURITY_")) return Category.SYSTEM;
        return Category.WORKFLOW;
    }
    public static boolean wantsEmail(NotificationPreference preference, String type) {
        if (!preference.isEmailEnabled()) return false;
        return switch (category(type)) {
            case WORKFLOW -> preference.isWorkflowEmail();
            case REMINDER -> preference.isReminderEmail();
            case ANNOUNCEMENT -> preference.isAnnouncementEmail();
            case SYSTEM -> preference.isSystemEmail();
        };
    }
}
