package com.omnibid.support;

import com.omnibid.dto.storage.StoredImage;
import com.omnibid.service.ImageStorageService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeImageStorageService implements ImageStorageService {

    private final AtomicInteger sequence = new AtomicInteger(0);
    private final List<String> uploadedPublicIds = new CopyOnWriteArrayList<>();
    private final List<String> deletedPublicIds = new CopyOnWriteArrayList<>();

    @Override
    public StoredImage upload(byte[] bytes, String folder) {
        int id = sequence.incrementAndGet();
        String publicId = folder + "/fake_" + id;
        String url = "https://res.cloudinary.com/demo/image/upload/" + publicId + ".png";
        uploadedPublicIds.add(publicId);
        return new StoredImage(url, publicId);
    }

    @Override
    public void delete(String publicId) {
        if (publicId != null && !publicId.isBlank()) {
            deletedPublicIds.add(publicId);
        }
    }

    public int uploadCount() {
        return uploadedPublicIds.size();
    }

    public int deleteCount() {
        return deletedPublicIds.size();
    }

    public List<String> uploadedPublicIds() {
        return List.copyOf(uploadedPublicIds);
    }

    public List<String> deletedPublicIds() {
        return List.copyOf(deletedPublicIds);
    }

    public void reset() {
        sequence.set(0);
        uploadedPublicIds.clear();
        deletedPublicIds.clear();
    }
}
