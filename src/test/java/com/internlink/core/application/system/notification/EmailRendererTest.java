package com.internlink.core.application.system.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.internlink.core.infrastructure.integration.email.EmailProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;

class EmailRendererTest {
    private final EmailRenderer renderer = new EmailRenderer(settings());

    private static EmailProperties settings() {
        var settings = new EmailProperties();
        settings.setPublicBaseUrl("https://internlink.test/");
        return settings;
    }

    private static Map<String, Object> sample(EmailTemplate template) {
        Map<String, Object> values = new HashMap<>();
        for (String name : template.variables()) values.put(name, "v-" + name);
        return values;
    }

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void rendersEveryTemplateWithSubjectTextAndHtml(EmailTemplate template) {
        var email = renderer.render(template, sample(template));

        assertThat(email.template()).isEqualTo(template.name());
        assertThat(email.subject()).isNotBlank().doesNotContain("\n").hasSizeLessThanOrEqualTo(255);
        assertThat(email.text()).isNotBlank().doesNotContain("[(${");
        assertThat(email.html()).contains("<html").contains("InternLink").doesNotContain(" th:");
    }

    @Test
    void forgotPasswordUsesFixedSubjectAndLinks() {
        var email = renderer.render(
                EmailTemplate.FORGOT_PASSWORD,
                Map.of("fullName", "An", "resetUrl", "https://internlink.test/reset?token=x",
                        "ttlMinutes", 60));

        assertThat(email.subject()).isEqualTo("[InternLink] Yêu cầu đặt lại mật khẩu");
        assertThat(email.text()).contains("https://internlink.test/reset?token=x").contains("60");
        assertThat(email.html()).contains("href=\"https://internlink.test/reset?token=x\"");
    }

    @Test
    void escapesHtmlButKeepsPlainTextAndDecodesSubject() {
        var values = sample(EmailTemplate.NOTIFICATION);
        values.put("title", "A & B <script>");
        values.put("message", "<script>alert(1)</script>");
        var email = renderer.render(EmailTemplate.NOTIFICATION, values);

        assertThat(email.subject()).isEqualTo("[InternLink] A & B <script>");
        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(email.text()).contains("<script>alert(1)</script>");
    }

    @Test
    void interviewHidesOptionalRowsWhenBlank() {
        var values = sample(EmailTemplate.INTERVIEW_INVITATION);
        values.put("interviewer", null);
        values.put("notes", "");
        var email = renderer.render(EmailTemplate.INTERVIEW_INVITATION, values);

        assertThat(email.subject()).isEqualTo("[v-companyName] Mời phỏng vấn vị trí v-jobTitle");
        assertThat(email.html()).doesNotContain("v-interviewer");
    }

    @Test
    void rejectsMissingVariables() {
        assertThatThrownBy(() -> renderer.render(EmailTemplate.FORGOT_PASSWORD, Map.of("fullName", "An")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
