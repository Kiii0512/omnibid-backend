package com.omnibid.service;

import com.omnibid.dto.storage.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    FileUploadResponse uploadFile(MultipartFile file, String folder);
    void deleteFile(String publicIdOrPath);
}