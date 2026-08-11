package com.nguyenvu.lopet.email;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Hai khoá Redis mà cả luồng đăng ký lẫn luồng đổi mật khẩu đều dựa vào:
 *
 * <ul>
 *   <li>{@code otp:<email>} — mã OTP, TTL 120 giây</li>
 *   <li>{@code email_verified:<email>} — bằng chứng người gọi kiểm soát hòm thư, TTL 300 giây</li>
 * </ul>
 *
 * Tên khoá và TTL là một phần hợp đồng giữa hai luồng, không phải chi tiết nội bộ — đổi ở đây là
 * phá luồng kia.
 */
@Component
@RequiredArgsConstructor
public class OtpStore {

    private static final Duration OTP_TTL = Duration.ofSeconds(120);
    private static final Duration VERIFIED_TTL = Duration.ofSeconds(300);

    private final StringRedisTemplate redis;

    public String otpKey(String email) {
        return "otp:" + email;
    }

    public String verifiedKey(String email) {
        return "email_verified:" + email;
    }

    public String findOtp(String email) {
        return redis.opsForValue().get(otpKey(email));
    }

    public void saveOtp(String email, String otp) {
        redis.opsForValue().set(otpKey(email), otp, OTP_TTL);
    }

    public void deleteOtp(String email) {
        redis.delete(otpKey(email));
    }

    public void markVerified(String email) {
        redis.opsForValue().set(verifiedKey(email), "true", VERIFIED_TTL);
    }

    public String findVerifiedFlag(String email) {
        return redis.opsForValue().get(verifiedKey(email));
    }

    public void deleteVerifiedFlag(String email) {
        redis.delete(verifiedKey(email));
    }

    /**
     * Đọc và tiêu thụ cờ trong MỘT lệnh nguyên tử (GETDEL). Tách làm get + del sẽ để hở cửa sổ cho
     * hai request đổi mật khẩu chạy song song cùng đọc thấy cờ và cùng đi tiếp — một lần xác thực
     * OTP mở ra nhiều lần đổi mật khẩu.
     */
    public String consumeVerifiedFlag(String email) {
        return redis.opsForValue().getAndDelete(verifiedKey(email));
    }
}
