package com.internlink.core.application.system.notification;

import java.util.UUID;

public record EmailDispatchContext(
        UUID initiatedBy,
        String sourceType,
        UUID sourceId,
        String scopeType,
        UUID scopeId) {
    public static EmailDispatchContext none() {
        return new EmailDispatchContext(null, null, null, null, null);
    }
}
