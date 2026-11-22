package com.omnibid.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer instantDisplayZoneCustomizer() {
        SimpleModule instantModule = new SimpleModule("OmnibidInstantModule");
        instantModule.addSerializer(Instant.class, new ValueSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializationContext ctxt) {
                if (value == null) {
                    gen.writeNull();
                    return;
                }
                String formatted = value.atZone(DateTimeConfig.DISPLAY_ZONE)
                        .toOffsetDateTime()
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                gen.writeString(formatted);
            }
        });
        instantModule.addDeserializer(Instant.class, new ValueDeserializer<>() {
            @Override
            public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
                String text = p.getString();
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return OffsetDateTime.parse(text.trim()).toInstant();
                } catch (DateTimeParseException ex) {
                    return (Instant) ctxt.handleWeirdStringValue(
                            Instant.class,
                            text,
                            "Invalid ISO-8601 date-time with offset (expected e.g. 2026-11-20T09:00:00+07:00 or 2026-11-20T02:00:00Z)"
                    );
                }
            }
        });
        return builder -> builder.addModule(instantModule);
    }
}
