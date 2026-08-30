package com.nguyenvu.lopet.common.media;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.nguyenvu.lopet.common.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    public static final String IMAGE = "image";
    public static final String VIDEO = "video";

    private final Cloudinary cloudinary;

    public String upload(MultipartFile file, String resourceType) {
        try {
            Map<String, Object> options = new HashMap<>();
            options.put("resource_type", resourceType);
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), options);
            return (String) result.get("secure_url");
        } catch (IOException exception) {
            throw new BadRequestException("Upload thất bại: " + exception.getMessage());
        }
    }

    public String uploadImage(MultipartFile file) {
        return upload(file, IMAGE);
    }
}
