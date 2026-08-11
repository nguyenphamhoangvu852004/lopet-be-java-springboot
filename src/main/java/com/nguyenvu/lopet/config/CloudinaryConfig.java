package com.nguyenvu.lopet.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cloudinary.Cloudinary;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(@Value("${lopet.cloudinary.cloud-name}") String cloudName,
                                 @Value("${lopet.cloudinary.api-key}") String apiKey,
                                 @Value("${lopet.cloudinary.api-secret}") String apiSecret) {
        return new Cloudinary(Map.of(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }
}
