package com.internlink.core.application.meeting;

import com.internlink.core.shared.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeetLinksTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "https://meet.google.com/abc-defg-hij",
            "meet.google.com/abc-defg-hij",
            "  HTTPS://Meet.Google.com/ABC-DEFG-HIJ  ",
            "http://meet.google.com/abc-defg-hij",
            "https://meet.google.com/abc-defg-hij?authuser=0",
            "https://meet.google.com/abc-defg-hij#x"
    })
    void normalizesValidMeetLinks(String raw) {
        assertThat(MeetLinks.normalize(raw)).isEqualTo("https://meet.google.com/abc-defg-hij");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "https://zoom.us/j/123",
            "https://meet.google.com.evil.com/abc-defg-hij",
            "https://evil.com/meet.google.com/abc-defg-hij",
            "https://meet.google.com/",
            "https://meet.google.com/abcdefghij",
            "https://meet.google.com/ab1-defg-hij",
            "javascript:alert(1)//meet.google.com/abc-defg-hij"
    })
    void rejectsAnythingElse(String raw) {
        assertThatThrownBy(() -> MeetLinks.normalize(raw))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("meet.google.com");
    }
}
