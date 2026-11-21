package com.omnibid.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Instant.class, source -> {
            if (source == null || source.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(source.trim()).toInstant();
            } catch (DateTimeParseException e) {
                return Instant.parse(source.trim());
            }
        });
    }
}
