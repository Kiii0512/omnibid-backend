package com.omnibid.service;

import com.omnibid.dto.storage.StorageUploadResult;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    StorageUploadResult upload(MultipartFile file, String folder);

    void delete(String publicId);
}

