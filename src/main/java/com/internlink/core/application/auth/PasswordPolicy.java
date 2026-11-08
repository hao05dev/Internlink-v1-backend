package com.internlink.core.application.auth;

import com.internlink.core.shared.exception.BadRequestException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    private PasswordPolicy() {}
    public static void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Mật khẩu mới cần ít nhất 8 ký tự và không quá 72 byte UTF-8.");
        }
    }
}
