package com.nguyenvu.lopet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.resend.Resend;

import lombok.extern.slf4j.Slf4j;

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
