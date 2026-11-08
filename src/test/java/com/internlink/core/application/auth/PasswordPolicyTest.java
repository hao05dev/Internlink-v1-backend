package com.internlink.core.application.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @Test void acceptsPassphraseAndRejectsShortOrOversizedUtf8Password() {
        assertThatCode(() -> PasswordPolicy.validate("a long memorable passphrase")).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate("Pass@12")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("😀".repeat(20))).isInstanceOf(RuntimeException.class);
        assertThatCode(() -> PasswordPolicy.validate("a".repeat(72))).doesNotThrowAnyException();
    }
}
