package com.nguyenvu.lopet.security.petcontext;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.nguyenvu.lopet.pet.repository.PetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Trả lời một câu hỏi duy nhất — "thú cưng này thuộc tài khoản nào?" — và trả lời nó ở mọi request
 * tương tác, nên nó phải rẻ.
 *
 * <p>Khoá Redis {@code pet:owner:<petId>} giữ accountId dưới dạng chuỗi, cùng quy ước đặt tên với
 * {@link com.nguyenvu.lopet.email.OtpStore}. TTL 30 phút là thoả hiệp: mapping này gần như bất biến
 * (chỉ đổi khi chuyển chủ hoặc ngừng hoạt động pet, và cả hai đều gọi {@link #invalidate}), nhưng
 * TTL hữu hạn giữ cho một lần miss invalidate không thành sai vĩnh viễn.
 *
 * <p><b>Không cache giá trị âm.</b> petId không tồn tại thì mỗi lần hỏi lại đi xuống DB — có chủ
 * đích: cache miss âm là bề mặt để ai đó bơm hàng triệu id rác vào Redis, mà truy vấn tra chủ sở hữu
 * chỉ đọc một cột theo khoá chính nên chi phí gần bằng không.
 *
 * <p>Redis hỏng KHÔNG được làm hỏng request: mọi lỗi khi đọc/ghi cache đều bị nuốt và rơi về DB.
 * Cache là thứ tăng tốc, không phải nguồn sự thật — để nó thành điểm chết đơn lẻ là đổi một sự cố
 * hạ tầng lấy một sự cố toàn hệ thống.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetOwnerResolver {

    private static final String KEY_PREFIX = "pet:owner:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final PetRepository petRepository;

    /** {@code null} = pet không tồn tại hoặc đã ngừng hoạt động ({@code @SQLRestriction} loại nó ra) */
    public Integer ownerAccountIdOf(Integer petId) {
        Integer cached = readCache(petId);
        if (cached != null) {
            return cached;
        }

        Integer ownerId = petRepository.findOwnerAccountId(petId).orElse(null);
        if (ownerId != null) {
            writeCache(petId, ownerId);
        }
        return ownerId;
    }

    /**
     * Gọi khi quyền sở hữu thay đổi: ngừng hoạt động pet, chuyển chủ. Bỏ bước này thì pet vừa bị tắt
     * vẫn qua được validate {@code X-Pet-Id} cho tới khi TTL hết hạn.
     */
    public void invalidate(Integer petId) {
        try {
            redis.delete(key(petId));
        } catch (RuntimeException exception) {
            // Không nuốt im lặng: một lần xoá cache hỏng nghĩa là tối đa 30 phút phân quyền theo dữ
            // liệu cũ, và đó là thứ phải tra được trong log khi đi truy nguyên sự cố.
            log.warn("Không xoá được cache chủ sở hữu của pet {}: {}", petId, exception.getMessage());
        }
    }

    private Integer readCache(Integer petId) {
        try {
            String value = redis.opsForValue().get(key(petId));
            return value == null ? null : Integer.valueOf(value);
        } catch (RuntimeException exception) {
            log.debug("Đọc cache chủ sở hữu thất bại, rơi về DB: {}", exception.getMessage());
            return null;
        }
    }

    private void writeCache(Integer petId, Integer ownerId) {
        try {
            redis.opsForValue().set(key(petId), String.valueOf(ownerId), TTL);
        } catch (RuntimeException exception) {
            log.debug("Ghi cache chủ sở hữu thất bại: {}", exception.getMessage());
        }
    }

    private String key(Integer petId) {
        return KEY_PREFIX + petId;
    }
}
