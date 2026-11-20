package com.omnibid.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.omnibid.service.StorageService;
import com.omnibid.service.impl.CloudinaryStorageServiceImpl;
import com.omnibid.service.impl.LocalStorageServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

@Configuration
public class StorageConfig {

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    @Bean
    @ConditionalOnProperty(prefix = "cloudinary", name = "cloud-name")
    public StorageService cloudinaryStorageService(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret
    ) {
        if (StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret)) {
            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName,
                    "api_key", apiKey,
                    "api_secret", apiSecret
            ));
            return new CloudinaryStorageServiceImpl(cloudinary);
        }
        return new LocalStorageServiceImpl(uploadDir);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(StorageService.class)
    public StorageService localStorageService() {
        return new LocalStorageServiceImpl(uploadDir);
    }
}


