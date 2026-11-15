package com.internlink.core.application.meeting;

import com.internlink.core.shared.exception.BadRequestException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Kiểm tra và chuẩn hóa link Google Meet do người dùng dán vào (chế độ MANUAL). */
public final class MeetLinks {
    private static final Pattern MEET_CODE = Pattern.compile("[a-z]{3}-[a-z]{4}-[a-z]{3}");

    private MeetLinks() {}

    /** Nhận "meet.google.com/abc-defg-hij" (có hoặc không có https, query) và trả link chuẩn. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw invalid();
        String value = raw.strip();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) lower = lower.substring(8);
        else if (lower.startsWith("http://")) lower = lower.substring(7);
        if (!lower.startsWith("meet.google.com/")) throw invalid();
        String code = lower.substring("meet.google.com/".length());
        int cut = indexOfAny(code, '?', '#', '/');
        if (cut >= 0) code = code.substring(0, cut);
        if (!MEET_CODE.matcher(code).matches()) throw invalid();
        return "https://meet.google.com/" + code;
    }

    private static int indexOfAny(String value, char... chars) {
        for (int i = 0; i < value.length(); i++) {
            for (char c : chars) if (value.charAt(i) == c) return i;
        }
        return -1;
    }

    private static BadRequestException invalid() {
        return new BadRequestException(
                "Link Google Meet không hợp lệ. Định dạng đúng: https://meet.google.com/abc-defg-hij");
    }
}
