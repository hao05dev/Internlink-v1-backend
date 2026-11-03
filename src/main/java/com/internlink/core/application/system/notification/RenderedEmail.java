package com.internlink.core.application.system.notification;

/** Email đã render từ template cố định: subject, bản text và bản HTML. */
public record RenderedEmail(String template, String subject, String text, String html) {}
