package com.nguyenvu.lopet.upload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.nguyenvu.lopet.common.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public List<UploadedFile> upload(Images images) {
        return upload(images, new Videos());
    }

    public List<UploadedFile> upload(Videos videos) {
        return upload(new Images(), videos);
    }

    public List<UploadedFile> upload(Images images, Videos videos) {
        List<UploadedFile> uploaded = new ArrayList<>();
        collectInto(uploaded, images.files(), UploadKind.IMAGE);
        collectInto(uploaded, videos.files(), UploadKind.VIDEO);
        return uploaded;
    }

    public String upload(MultipartFile file, UploadKind kind) {
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", kind.resourceType());
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);
            return (String) result.get("secure_url");
        } catch (IOException exception) {
            throw new BadRequestException("Upload failed: " + exception.getMessage());
        }
    }
    private void collectInto(List<UploadedFile> target, MultipartFile[] files, UploadKind kind) {
        if (files == null) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            target.add(new UploadedFile(upload(file, kind), kind));
        }
    }
}
