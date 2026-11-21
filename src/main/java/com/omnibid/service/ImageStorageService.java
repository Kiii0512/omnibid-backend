package com.omnibid.service;

import com.omnibid.dto.storage.StoredImage;

public interface ImageStorageService {

    StoredImage upload(byte[] bytes, String folder);

    void delete(String publicId);
}
