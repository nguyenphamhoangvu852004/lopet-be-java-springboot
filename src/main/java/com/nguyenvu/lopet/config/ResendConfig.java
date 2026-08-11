package com.nguyenvu.lopet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.resend.Resend;

import lombok.extern.slf4j.Slf4j;

/**
 * Client Resend dùng chung cho toàn app. {@code new Resend(apiKey)} không gọi mạng và không kiểm tra
 * khoá, nên context vẫn lên được khi khoá sai — lỗi chỉ lộ ra ở lần gửi đầu tiên. Cảnh báo dưới đây
 * tồn tại để trường hợp thiếu khoá không im lặng cho tới lúc user bấm "gửi OTP".
 */
@Slf4j
@Configuration
public class ResendConfig {

    @Bean
    public Resend resend(@Value("${lopet.mail.resend.api-key}") String apiKey) {
        if (apiKey.isBlank()) {
            log.warn("RESEND_API_KEY đang trống — mọi lần gửi mail sẽ bị Resend trả về 401.");
        }
        return new Resend(apiKey);
    }
}
