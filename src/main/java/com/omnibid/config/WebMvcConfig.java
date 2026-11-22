package com.omnibid.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.time.OffsetDateTime;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, Instant>() {
            @Override
            public Instant convert(String source) {
                if (source == null || source.isBlank()) {
                    return null;
                }
                return OffsetDateTime.parse(source.trim()).toInstant();
            }
        });
    }
}
