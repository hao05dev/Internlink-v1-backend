package com.internlink.core.infrastructure.config;

import com.internlink.core.presentation.organization.dto.response.AcademicProgramResponse;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisConfigTest {

    private final GenericJackson2JsonRedisSerializer serializer =
            new GenericJackson2JsonRedisSerializer(RedisConfig.createRedisObjectMapper());

    private AcademicProgramResponse program() {
        return AcademicProgramResponse.builder()
                .id(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .code("CNTT")
                .name("Công nghệ thông tin")
                .isActive(true)
                .createdAt(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC))
                .build();
    }

    @Test
    void arrayListOfResponsesRoundTrips() {
        List<AcademicProgramResponse> list = Stream.of(program()).collect(Collectors.toList());
        Object restored = serializer.deserialize(serializer.serialize(list));
        assertThat(restored).isEqualTo(list);
    }

    @Test
    void unreadableEntryIsEvictedInsteadOfFailing() {
        Cache cache = mock(Cache.class);
        when(cache.getName()).thenReturn("academic_programs");

        new RedisConfig.EvictOnReadErrorHandler()
                .handleCacheGetError(new SerializationException("bad entry"), cache, "all");

        verify(cache).evict("all");
    }
}
