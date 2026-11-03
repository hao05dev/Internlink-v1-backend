package com.internlink.core.application.system.notification;

public record EmailDeliveryFilter(
        String status, String scopeType, String sourceType, String templateKey, String recipient) {}
