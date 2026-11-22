package com.omnibid.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class DateTimeConfig {

    public static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final ZoneId DEFAULT_ZONE = DISPLAY_ZONE;

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SimpleModule instantIsoModule() {
        SimpleModule module = new SimpleModule("InstantIsoModule");
        module.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null) {
                    gen.writeNull();
                } else {
                    String formatted = value.atZone(DISPLAY_ZONE)
                            .toOffsetDateTime()
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                    gen.writeString(formatted);
                }
            }
        });
        module.addDeserializer(Instant.class, new JsonDeserializer<Instant>() {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                String text = p.getText();
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return OffsetDateTime.parse(text.trim()).toInstant();
                } catch (DateTimeParseException e) {
                    return Instant.parse(text.trim());
                }
            }
        });
        return module;
    }

    @Bean
    public ObjectMapper objectMapper(SimpleModule instantIsoModule) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(instantIsoModule);
        return mapper;
    }
}
