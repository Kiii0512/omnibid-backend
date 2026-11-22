package com.omnibid.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.omnibid.service.ImageStorageService;
import com.omnibid.service.impl.CloudinaryImageStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration
public class CloudinaryConfig {

    @Bean
    @ConditionalOnMissingBean(ImageStorageService.class)
    public ImageStorageService imageStorageService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret
    ) {
        if (StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret)) {
            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim(),
                    "secure", true
            ));
            return new CloudinaryImageStorageService(cloudinary);
        }

        log.warn("Thiếu cấu hình Cloudinary (cloudinary.cloud-name/api-key/api-secret); mọi yêu cầu tải ảnh sẽ trả 502 IMAGE_UPLOAD_FAILED");
        return new CloudinaryImageStorageService(null);
    }
}
