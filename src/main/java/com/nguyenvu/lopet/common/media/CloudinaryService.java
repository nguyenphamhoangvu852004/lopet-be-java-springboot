package com.nguyenvu.lopet.common.media;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.nguyenvu.lopet.common.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

/**
 * Thay cho cặp {@code multer + cloudinary} bên TS. Điểm khác về kỹ thuật: Express ghi file ra đĩa
 * tạm rồi upload theo đường dẫn, Spring giữ luôn trong bộ nhớ và upload theo mảng byte — kết quả
 * trả về ({@code secure_url}) và thứ tự tác dụng phụ thì giống hệt.
 *
 * <p>Mọi lời gọi upload phải nằm SAU các tầng kiểm quyền: bản TS cố ý đặt middleware trước
 * {@code upload.single()} để request bị từ chối không kịp tốn một lượt upload.
 */
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    public static final String IMAGE = "image";
    public static final String VIDEO = "video";

    private final Cloudinary cloudinary;

    /** @return {@code secure_url} của tài nguyên vừa upload */
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

    /** Bản mặc định resource_type=image — dùng cho group/comment/message/ads */
    public String uploadImage(MultipartFile file) {
        return upload(file, IMAGE);
    }
}
