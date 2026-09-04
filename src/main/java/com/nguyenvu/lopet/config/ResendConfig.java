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
            log.warn("RESEND_API_KEY is empty — every mail send will be rejected by Resend with a 401.");
        }
        return new Resend(apiKey);
    }
}
