package com.omnibid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeConfigTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DateTimeConfig config = new DateTimeConfig();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(config.instantIsoModule());
    }

    @Test
    @DisplayName("Instant is serialized with Asia/Ho_Chi_Minh offset (+07:00)")
    void serializeInstant_FormatsToPlus07Offset() throws Exception {
        Instant instant = Instant.parse("2026-11-20T02:00:00Z");
        String json = objectMapper.writeValueAsString(instant);

        assertThat(json).isEqualTo("\"2026-11-20T09:00:00+07:00\"");
    }

    @Test
    @DisplayName("Instant deserializes UTC ISO-8601 string (ending in Z)")
    void deserializeInstant_FromUtcString() throws Exception {
        String json = "\"2026-11-20T02:00:00Z\"";
        Instant parsed = objectMapper.readValue(json, Instant.class);

        assertThat(parsed).isEqualTo(Instant.parse("2026-11-20T02:00:00Z"));
    }

    @Test
    @DisplayName("Instant deserializes +07:00 offset ISO-8601 string")
    void deserializeInstant_FromPlus07String() throws Exception {
        String json = "\"2026-11-20T09:00:00+07:00\"";
        Instant parsed = objectMapper.readValue(json, Instant.class);

        assertThat(parsed).isEqualTo(Instant.parse("2026-11-20T02:00:00Z"));
    }
}
