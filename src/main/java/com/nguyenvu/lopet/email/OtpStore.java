package com.nguyenvu.lopet.email;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

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

    public String consumeVerifiedFlag(String email) {
        return redis.opsForValue().getAndDelete(verifiedKey(email));
    }
}
