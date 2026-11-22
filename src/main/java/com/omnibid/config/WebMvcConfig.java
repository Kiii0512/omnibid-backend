package com.omnibid.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;

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

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        boolean foundMappingJackson2 = false;
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                foundMappingJackson2 = true;
                List<MediaType> mediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                if (!mediaTypes.contains(MediaType.TEXT_PLAIN)) {
                    mediaTypes.add(MediaType.TEXT_PLAIN);
                }
                if (!mediaTypes.contains(MediaType.APPLICATION_OCTET_STREAM)) {
                    mediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
                }
                jacksonConverter.setSupportedMediaTypes(mediaTypes);
            } else if (converter instanceof JacksonJsonHttpMessageConverter jacksonJsonConverter) {
                List<MediaType> mediaTypes = new ArrayList<>(jacksonJsonConverter.getSupportedMediaTypes());
                if (!mediaTypes.contains(MediaType.TEXT_PLAIN)) {
                    mediaTypes.add(MediaType.TEXT_PLAIN);
                }
                if (!mediaTypes.contains(MediaType.APPLICATION_OCTET_STREAM)) {
                    mediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
                }
                jacksonJsonConverter.setSupportedMediaTypes(mediaTypes);
            }
        }

        if (!foundMappingJackson2 && objectMapper != null) {
            MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
            List<MediaType> mediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
            if (!mediaTypes.contains(MediaType.TEXT_PLAIN)) {
                mediaTypes.add(MediaType.TEXT_PLAIN);
            }
            if (!mediaTypes.contains(MediaType.APPLICATION_OCTET_STREAM)) {
                mediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
            }
            converter.setSupportedMediaTypes(mediaTypes);
            converters.add(converter);
        }
    }
}
